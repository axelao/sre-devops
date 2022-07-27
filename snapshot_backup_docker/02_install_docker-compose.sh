#!/bin/bash

#Author: Axel Danieles
#Email: axel.danieles@gmail.com

####################################################INSTALL DOCKER-COMPOSE ON DEBIAN################################################################

#1 - Install Compose on Linux
echo "Install Compose on Linux"
sudo curl -L "https://github.com/docker/compose/releases/download/1.29.1/docker-compose-$(uname -s)-$(uname -m)" \
	-o /usr/local/bin/docker-compose

#2 - Apply executable permissions to the binary
echo "Apply executable permissions to the binary"
sudo chmod +x /usr/local/bin/docker-compose

#3 - Test the installation
echo "Test the installation"
docker-compose --version

echo "FINISH INSTALLATION"
