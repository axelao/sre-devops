from collections import defaultdict
import boto3
import json
sns = boto3.client('sns')
ec2 = boto3.resource('ec2')
sns_arn = 'arn:aws:sns:region:account:name_sns'


def lambda_handler(event, context):
    filters = [{
            'Name': 'tag:Stop',
            'Values': ['true']
        },
        {
            'Name': 'instance-state-name',
            'Values': ['running']
        }
    ]

    instances = ec2.instances.filter(Filters=filters)

    runningInstances = [instance.id for instance in instances]

    ec2info = defaultdict()
    for instance in instances:
        for tag in instance.tags:
            if 'Name'in tag['Key']:
                name = tag['Value']
        # Add instance info to a dictionary
        ec2info[instance.id] = {
            'Name': name,
            'Type': instance.instance_type,
            'State': instance.state['Name'],
            'Private IP': instance.private_ip_address,
            'Public IP': instance.public_ip_address
        }

    if len(runningInstances) > 0:
        for running in runningInstances:
            print("Parando instancia: " + ec2info[running]['Name'])
        shuttingDown = ec2.instances.filter(InstanceIds=runningInstances).stop()

        instanceResponse = []
        for response in shuttingDown:
            if '200' not in str(response['ResponseMetadata']['HTTPStatusCode']):
                instanceResponse.append(str(ec2info[response['StoppingInstances'][0]['InstanceId']]['Name']) + " " + str(response['StoppingInstances'][0]['CurrentState']['Name']))
        
    if len(instanceResponse) > 0:
        response = sns.publish(
            TopicArn=sns_arn,
            Subject='Problemas al detener instancia',
            Message=str(instanceResponse),
            MessageStructure='string')