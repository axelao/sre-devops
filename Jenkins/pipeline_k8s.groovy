#! groovy
def flujo() 
{
    node ("${env.AGENT}")
    {
        disableConcurrentBuilds()
        stage('Checkout SCM'){
            checkout scm
            try {
                repoName = sh(script: 'cat .git/config | grep url | cut -d = -f 2 | xargs basename -s .git', returnStdout: true).trim()
            } catch (Exception ex) {
                error('Error nombre de repositorio ' + ex)
            }
        }
        stage('App Version')
        {
            // tomo version del package para user de tag en el deploy
            Date currentDate = new Date()
            env.APP_VERSION = BUILD_NUMBER+currentDate.getTime()
        }
        
        stage('Set framework ENV'){
            // cargo environments del repositorio en el nodo
            dir('devops'){
                unstash 'Framework'
                try{
                    ocpFunctions = load 'openshift/functions.groovy'
                }catch(Exception e){
                    echo "No cargo las funciones para openshift" + e.toString()
                }
            }
            def loadvar = load 'devops/environment/openshift/' + repoName + '.groovy'
            try {
                loadvar.setenv()
            }catch (Exception e){
                echo 'No existen variables de entorno ' + e.toString()
            }
        }
        
        //Unit test y analisis de SONAR
        if (binding.hasVariable('SONAR')){
            stash name: 'sonarDir', useDefaultExcludes: false 
            def sonarArch = load 'devops/flujos/openshift/standard-sonar-python.groovy'
            sonarArch.flujo()
        }
            
        // TODO test de la aplcacion
        stage('Check project permissions'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        def saSelector = openshift.selector( 'serviceaccount' )
                        saSelector.withEach {
                            echo "Service account: ${it.name()} is defined in ${openshift.project()}"
                        }
                    }
                }
            }
        }

        stage('Build secrets'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        //Elimino secrets antes de su creación para evitar inconsistencia de datos en los valores de los secrets
                        ocpFunctions.objectDelete('secret', 'django-admin')
                        ocpFunctions.objectDelete('secret', 'bitbucket')

                        //Generación de credenciales para conectarse a Bitbucket.
                        ocpFunctions.buildSecret('svc_bb_ocp.yaml')                            
                    }
                }
            }
        }

        stage ('Apply Templates'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        //Se Aplica template para python
                        openshift.apply(openshift.process(readFile(file: env.APP_TEMPLATE), '-p', "OCP_PROJECT=${env.OCP_PROJECT}", '-p', "APP_NAME=${repoName}", '-p',"APP_VERSION=${env.APP_VERSION}", '-p',"APP_BRANCH=${env.BRANCH_NAME}", '-p', "ENV=${env.URL_ENV}", '-p', "OCP_APPNAME=${env.OCP_APPNAME}", '-p',"CLUSTER_ENV=${env.CLUSTER_ENV}", '-p',"DIR_SOURCE=${env.DIR_SOURCE}"))

                       
                        if(binding.hasVariable('WS_TEMPLATE')){                       
                            //Se Aplica template para nginx en caso de que se necesite crear un nginx
                            println "Aplicando template nginx"
                            openshift.apply(openshift.process(readFile(file: env.wsTemplate), '-p', "NGINX_NAME=${env.NGINX_NAME}", '-p', "APP_NAME=${repoName}", '-p',"OCP_APPNAME=${env.OCP_APPNAME}", '-p',"ENV=${env.URL_ENV}", '-p', "CLUSTER_ENV=${env.CLUSTER_ENV}", '-p', "NGINX_VERSION=${env.NGINX_VERSION}", '-p', "CONFIG_FILE_NGINX=${env.CONFIG_FILE_NGINX}"))
                            
                            //Se crea la ruta con certificado en el namespace indicado.
                            if(env.BRANCH_NAME == "master"){
                                def fileTemplate = "route-Prod-nginx"
                                ocpFunctions.createSecureRouteNginx("${fileTemplate}")
                            }else{
                                def fileTemplate = "route-noProd-nginx"
                                ocpFunctions.createSecureRouteNginx("${fileTemplate}")
                            }                               
                        }                                                     
                    }
                }
            }
        }

        //Sección donde se despliega REDIS o conectamos REDIS con una app/microservicio/api.
        if(binding.hasVariable('REDIS_VERSION')){
            stage('REDIS'){
                env.DB_TEMPLATE="devops/openshift/template/stardard-redis.yaml"
                openshift.withCluster(OCP_CLUSTER){
                    openshift.withCredentials(OCP_TOKEN){
                        openshift.withProject(OCP_PROJECT){
                            //Genero el redis secret en el namespace.
                            ocpFunctions.buildSecret('redis-auth')
                            //Se aplica el template de redis.
                            openshift.apply(openshift.process(readFile(file: env.DB_TEMPLATE), '-p', "REDIS_VERSION=${REDIS_VERSION}",  '-p', "DB_REDIS_NAME=${DB_REDIS_NAME}"))
                            //Rollout dc "redis"
                            def dcRedis = openshift.selector('dc', "${DB_REDIS_NAME}")
                            dcRedis.rollout().latest()
                            sleep(10)                                   
                            //Agrego redis secret al "dc" ${repoName}
                            def output = openshift.raw("set env dc/${repoName} --from=secret/redis-auth")
                        }
                    }
                }
            }
        }else if(binding.hasVariable('DB_REDIS_NAME')){
            stage('Connecting with REDIS'){
                openshift.withCluster(OCP_CLUSTER){
                    openshift.withCredentials(OCP_TOKEN){
                        openshift.withProject(OCP_PROJECT){
                            //Agrego redis secret al "dc" ${repoName}
                            def output = openshift.raw("set env dc/${repoName} --from=secret/redis-auth")
                        }
                    }
                }
            }
        }

        stage('Build'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        def bc = openshift.selector('bc', "${repoName}")
                        def buildSelector                           
                        try {
                            buildSelector = bc.startBuild()
                        }catch(Exception e){
                            println "Verificar build en openshift " +e.toString()
                            println "Cancelando despliegue..."
                            currentBuild.result = 'FAILURE'
                            throw(e)
                        }
                        try{
                            buildSelector.logs('-f')
                        }catch(Exception e){
                            println "Error al descargar los logs"
                            println e.toString()
                        }                                
                        
                        println "Esperando finalizar el build..."                                                        
                        timeout(40){   
                            buildSelector.untilEach(1){
                                if(it.object().status.phase == "Failed" || it.object().status.phase == "Error"){
                                    println "Fallo al construir imagen"
                                    currentBuild.result = 'FAILURE'
                                }
                                return it.object().status.phase == "Complete"
                            }
                        }

                        // Borramos secret del namespace.
                        ocpFunctions.objectDelete('secret', 'bitbucket')
                    }
                }
            }
        }

        if(binding.hasVariable('OCP_LOAD_SECRET') || binding.hasVariable('OCP_LOAD_CONFIGMAP')){
            stage('Project secrets & configmaps'){
                openshift.withCluster(OCP_CLUSTER){
                    openshift.withCredentials(OCP_TOKEN){
                        openshift.withProject(OCP_PROJECT){
                            if(binding.hasVariable('FILE_FROM_JENKINS')){
                                if(binding.hasVariable('OCP_LOAD_SECRET')){
                                    JENKINS_SECRETS_LIST = ocpFunctions.fileFromJenkins(OCP_LOAD_SECRET)
                                }
                                if(binding.hasVariable('OCP_LOAD_CONFIGMAP')){
                                    JENKINS_CONFIGMAP_LIST = ocpFunctions.fileFromJenkins(OCP_LOAD_CONFIGMAP)
                                }
                            }else{
                                if(binding.hasVariable('OCP_LOAD_SECRET')){
                                        def secretFiles = sh(script: 'for i in $(find deployment/secrets -type f); do echo $i; done', returnStdout: true).trim().tokenize()
                                        println "Archivos de secret: "+secretFiles
                                        SECRETS_LIST = ocpFunctions.fileFromRepo(secretFiles)
                                }
                                if(binding.hasVariable('OCP_LOAD_CONFIGMAP')){
                                        def configmapFiles = sh(script: 'for i in $(find deployment/configmaps -type f); do echo $i; done', returnStdout: true).trim().tokenize()
                                        println "Archivos de configmap: "+configmapFiles
                                        CONFIGMAP_LIST = ocpFunctions.fileFromRepo(configmapFiles)
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Apply Env'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        // los archivos deben ser nombrados como las variables de entorno
                        println "Configurando entorno..."
                        if(binding.hasVariable('OCP_LOAD_ENVIRONMENT')){
                            ocpFunctions.loadAppEnv(OCP_LOAD_ENVIRONMENT, "environment")
                        }
                        if(binding.hasVariable('FILE_FROM_JENKINS')){
                            if(binding.hasVariable('OCP_LOAD_SECRET')){
                                ocpFunctions.loadAppEnv(JENKINS_SECRETS_LIST, "secret")
                            }
                            if(binding.hasVariable('OCP_LOAD_CONFIGMAP')){
                                ocpFunctions.loadAppEnv(JENKINS_CONFIGMAP_LIST, "configmap")
                            }
                        }else{
                            if(binding.hasVariable('OCP_LOAD_SECRET')){
                                ocpFunctions.loadAppEnv(SECRETS_LIST, "secret")
                            }
                            if(binding.hasVariable('OCP_LOAD_CONFIGMAP')){
                                ocpFunctions.loadAppEnv(CONFIGMAP_LIST, "configmap")
                            }
                        }
                        println "Cargando TimeZone"
                        try {
                            def output = openshift.raw("set env dc/${repoName} TZ=America/Argentina/Buenos_Aires")
                        }catch(Exception e){
                            println e
                        }
                    }
                }
            }
        }     

        stage('Deploy'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        println "Aplicando cambios..."
                        def dc = openshift.selector('dc', "${repoName}")
                        dc.rollout().latest()
                        dc.rollout().status()
                        println "Regenarando pod"
                        sleep(8)
                        timeout(5) {
                            openshift.selector('dc', "${repoName}").related('pods').untilEach(1) {
                                println "Iniciando pod..."
                                println it.object().status.phase
                                if(it.object().status.phase == "Failed"){
                                    println "Failed"
                                    currentBuild.result = 'FAILURE'
                                }
                                return (it.object().status.phase == "Running")
                            }
                        }                     
                    }
                }
            }
        }

        stage('App info'){
            openshift.withCluster(OCP_CLUSTER){
                openshift.withCredentials(OCP_TOKEN){
                    openshift.withProject(OCP_PROJECT){
                        ROUTE = openshift.selector('routes', "${OCP_APPNAME}").object().spec.host
                        println "URL del servicio: https://"+ROUTE
                    }
                }
            }
        }

        //Clean Workspace en Jenkins
        cleanWs cleanWhenSuccess: true
    }
}
return this;