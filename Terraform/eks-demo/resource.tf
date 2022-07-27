### OIDC config and associate with k8s cluster
resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = concat([data.tls_certificate.eks.certificates.0.sha1_fingerprint], var.oidc_thumbprint_list)
  url             = data.aws_eks_cluster.eks.identity.0.oidc.0.issuer
}

### Install ALB controller with helm on EKS
resource "helm_release" "ingress" {
  depends_on = [module.eks.cluster_id]
  name       = "aws-load-balancer-controller"
  chart      = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  namespace  = "kube-system"
  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  set {
    name  = "serviceAccount.create"
    value = "false"
  }  

  set {
    name  = "clusterName"
    value = local.cluster_name
  }
  set {
    name  = "nodeSelector.environment"
    value = "core-app"
  }  
}

### Associate role in ALB controller
resource "kubernetes_service_account" "aws-load-balancer-controller" {
  depends_on = [module.eks.cluster_id]
  metadata {
    name = "aws-load-balancer-controller"
    namespace = "kube-system"
    annotations = {
        "eks.amazonaws.com/role-arn" = aws_iam_role.eks-service-account-role.arn
    }     
  } 
  automount_service_account_token = true
}

### Create role for service account associate with ALB
resource "aws_iam_role" "eks-service-account-role" {
  name = "eks_alb_demo"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = ["sts:AssumeRoleWithWebIdentity"]
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Federated = aws_iam_openid_connect_provider.eks.arn
        }
      }
    ]
  })
}

### Attach policy for ALB
resource "aws_iam_role_policy_attachment" "attach-role-a" {
  role       = aws_iam_role.eks-service-account-role.name
  policy_arn = data.aws_iam_policy.eks-lb-a.arn
}
resource "aws_iam_role_policy_attachment" "attach-role-b" {
  role       = aws_iam_role.eks-service-account-role.name
  policy_arn = data.aws_iam_policy.eks-lb-b.arn
}

# Create namespace
resource "kubernetes_namespace" "namespaces" {
  depends_on = [module.eks.cluster_id]
  metadata {
    name = var.namespace
  }
}

### Install APM - Prometheus
resource "helm_release" "prometheus" {
  depends_on = [module.eks.cluster_id]
  name       = "prometheus"
  chart      = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  namespace  = "prometheus"

  set {
    name  = "clusterName"
    value = local.cluster_name
  }
}

### Install secret manager 
resource "helm_release" "secret-manager" {
  depends_on = [module.eks.cluster_id]
  name       = "secrets-store-csi-driver"
  chart      = "secrets-store-csi-driver"
  repository = "https://raw.githubusercontent.com/kubernetes-sigs/secrets-store-csi-driver/master/charts"
  namespace  = "kube-system"
  set {
    name  = "syncSecret.enabled"
    value = "true"
  } 
}

### Install Metrics Server for HPA
resource "helm_release" "metrics-server" {
  depends_on = [module.eks.cluster_id]
  name       = "metrics-server"
  chart      = "metrics-server"
  repository = "https://kubernetes-sigs.github.io/metrics-server/"
  namespace  = "kube-system"
  set {
    name  = "nodeSelector.environment"
    value = "core-app"
  }
  set {
    name  = "replicas"
    value = "3"
  }  
}

### Install fluentD and aws-provider-csi-driver on cluster EKS
resource "null_resource" "fluentD" {
  depends_on = [module.eks.cluster_id]
  provisioner "local-exec" {
    command = "aws eks --region us-east-1 update-kubeconfig --name ${local.cluster_name}; kubectl apply -f fluentd-dvuat.yml; kubectl apply -f secrets-store-csi-driver-provider-aws.yml"   
  }
}