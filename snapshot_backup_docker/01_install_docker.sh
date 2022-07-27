#!/bin/bash

#Author: Axel Danieles
#Email: axel.danieles@gmail.com

####################################################INSTALL DOCKER ENGINE ON DEBIAN################################################################

#1 - Uninstall old versions
echo "Uninstall old versions docker"
sudo apt-get remove -y docker docker-engine docker.io containerd runc

#2 - Set up the repository
echo "Set up the repository"
sudo aptitude update
sudo aptitude install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

#3 - Add Docker’s official GPG key
echo "Add Docker’s official GPG key"
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

#4 - Set up the stable repository
echo "Set up the stable repository"
echo \
  "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

#5 - Install Docker Engine
echo "Install Docker Engine"
sudo aptitude update
sudo aptitude install -y docker-ce docker-ce-cli containerd.io 

#6 - Test the installation
echo "Test the installation"
sudo docker --version

echo "FINISH INSTALLATION"
