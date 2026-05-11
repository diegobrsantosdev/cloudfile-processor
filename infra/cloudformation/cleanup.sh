#!/bin/bash

# Usage: sh cleanup.sh dev
ENVIRONMENT=$1
PROJECT_NAME="file-processor"

if [ -z "$ENVIRONMENT" ]; then
  echo "Error: Environment not specified."
  exit 1
fi

echo "--------------------------------------------------------"
echo "STARTING INFRASTRUCTURE DESTRUCTION ($ENVIRONMENT)"
echo "--------------------------------------------------------"

# 1. ECS
echo "Deleting ECS..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-ecs-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-ecs-${ENVIRONMENT}

# 2. ALB
echo "Deleting ALB..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-alb-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-alb-${ENVIRONMENT}

# 3. SSM
echo "Deleting SSM..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-ssm-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-ssm-${ENVIRONMENT}

# 4. Cognito
echo "Deleting Cognito..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-cognito-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-cognito-${ENVIRONMENT}

# 5. S3
echo "Deleting S3..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-s3-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-s3-${ENVIRONMENT}

# 6. DynamoDB
echo "Deleting DynamoDB..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-dynamodb-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-dynamodb-${ENVIRONMENT}

# 7. Network (VPC & NAT Gateway)
echo "Deleting Network..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-network-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-network-${ENVIRONMENT}

# 8. SQS
echo "Deleting SQS..."
aws cloudformation delete-stack --stack-name ${PROJECT_NAME}-sqs-${ENVIRONMENT}
aws cloudformation wait stack-delete-complete --stack-name ${PROJECT_NAME}-sqs-${ENVIRONMENT}

echo "--------------------------------------------------------"
echo "SUCCESS: ALL STACKS REMOVED."
echo "--------------------------------------------------------"