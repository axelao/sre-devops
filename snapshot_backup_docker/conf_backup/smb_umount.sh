#Autor: Axel Danieles
#Email: axel.danieles@gmail.com

#!/bin/bash

#Umount smb_fs in server

#echo "Umount FS (Windows Server) from Linux"
date +"%d-%m-%y %T"
umount /data
if [ $? -ne "0" ]; then
   echo "Failed to umount fs_share"
   exit 1;
fi;


