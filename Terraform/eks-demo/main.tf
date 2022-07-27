locals {
    cluster_name = "eks-cluster-demo"
}

data "aws_eks_cluster" "eks" {
  name = module.eks.cluster_id
}

data "aws_eks_cluster_auth" "eks" {
  name = module.eks.cluster_id
}

data "aws_iam_policy" "eks-lb-a" {
  arn = "arn:aws:iam::549562309487:policy/AWSLoadBalancerControllerIAMPolicy"
}

data "aws_iam_policy" "eks-lb-b" {
  arn = "arn:aws:iam::549562309487:policy/AWSLoadBalancerControllerAdditionalIAMPolicy"
}

data "aws_iam_policy" "cloudwatch-full-logs" {
  arn = "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess"
}

data "tls_certificate" "eks" {
  url = data.aws_eks_cluster.eks.identity.0.oidc.0.issuer
}

module "eks" {
  source          = "terraform-aws-modules/eks/aws"
  version         = "17.24.0"

  cluster_version = "1.19"
  cluster_name    = local.cluster_name
  vpc_id          = "vpc-0d267947c2c1e9bb9"
  subnets         = ["subnet-09c6b7b3a936f8fe6", "subnet-0f8080bb64e4410e9", "subnet-095c5324b76b5bc30"]

  cluster_endpoint_private_access = "true"
  cluster_endpoint_public_access  = "true"

  # write_kubeconfig      = true
  # manage_aws_auth       = true

  workers_additional_policies = [data.aws_iam_policy.eks-lb-a.arn, data.aws_iam_policy.cloudwatch-full-logs.arn]
  node_groups = {
    worker-core = {
      create_launch_template = true
      key_name               = "eks-demo"       #Key-pair name in AWS
      instance_types         = ["t3.medium"]
      desired_capacity       = 2
      max_capacity           = 3
      min_capacity           = 2

      disk_size       = 30
      disk_type       = "gp2"

      k8s_labels = {
        environment = "core-app"
      }
      additional_tags = {
        Proyecto = "Demo"
        Owner    = "Axel Danieles"
        env      = "dev"
      }
    }
    worker-dev = {
      create_launch_template = true
      key_name               = "eks-demo"       #Key-pair name in AWS
      instance_types         = ["t3.medium"]
      desired_capacity       = 5
      max_capacity           = 10
      min_capacity           = 5

      disk_size       = 30
      disk_type       = "gp3"

      k8s_labels = {
        environment = "apps"
      }
      additional_tags = {
        Proyecto = "Demo"
        Owner    = "Axel Danieles"
        env      = "dev"
      }
    }   
  }
}
