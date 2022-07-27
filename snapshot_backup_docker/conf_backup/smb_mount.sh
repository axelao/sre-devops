#Autor: Axel Danieles
#Email: axel.danieles@gmail.com

#!/bin/bash

#Mount smb_fs in server
#name="USERNAME"
#pass="PASSWORD"


#echo "Mount FS from Windows Server - CLIENT"
date +"%d-%m-%y %T"
#mount -t cifs -v //ip_address/dir$ /data -o user=$name,password=$pass
if [ $? -ne "0" ]; then
   echo "Failed to mount fs_share on the server"
   exit 1;
fi;


