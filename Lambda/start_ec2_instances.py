from collections import defaultdict
import boto3
import json
sns = boto3.client('sns')
ec2 = boto3.resource('ec2')
sns_arn = 'arn:aws:sns:us-east-1:account:name_sns'

def lambda_handler(event, context):
    filters = [{
            'Name': 'tag:Start', 
            'Values': ['true'] 
        },
        {
            'Name': 'instance-state-name', 
            'Values': ['stopped']
        }
    ]
    
    instances = ec2.instances.filter(Filters=filters)

    stoppedInstances = [instance.id for instance in instances]

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
        
    if len(stoppedInstances) > 0:
        for stopped in stoppedInstances:
            print("Iniciando instancia: " + ec2info[instance.id]['Name'])
        startingUp = ec2.instances.filter(InstanceIds=stoppedInstances).start()
        instanceResponse = []
        for response in startingUp:
            if '200' not in str(response['ResponseMetadata']['HTTPStatusCode']):
                instanceResponse.append(str(ec2info[response['StartingInstances'][0]['InstanceId']]['Name']) + " " + str(response['StartingInstances'][0]['CurrentState']['Name']))
    
    if len(instanceResponse) > 0:
        response = sns.publish(
            TopicArn=sns_arn,
            Subject='Problemas al iniciar instancia',
            Message=str(instanceResponse),
            MessageStructure='string')