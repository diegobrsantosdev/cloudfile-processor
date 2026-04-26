#!/bin/bash
set -e

ENV=${1:-dev}
PROJECT_NAME="file-processor"
REGION="us-east-1"
CALLBACK_URL=${CALLBACK_URL:-"http://localhost:8080/login"}
LOGOUT_URL=${LOGOUT_URL:-"http://localhost:8080/logout"}

echo "Starting deployment for environment: ${ENV}"

deploy() {
  local template=$1
  local stack_name=$2
  shift 2
  echo ""
  echo "▶ Deploying ${stack_name}..."
  aws cloudformation deploy \
    --template-file "./core/${template}" \
    --stack-name "${stack_name}" \
    --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
    --region "${REGION}" \
    --no-fail-on-empty-changeset \
    --parameter-overrides "$@"
}

deploy 01-network.yml       "${PROJECT_NAME}-network-${ENV}"   \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"

deploy 02-ecr.yml           "${PROJECT_NAME}-ecr-${ENV}"       \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"

deploy 03-dynamodb.yml      "${PROJECT_NAME}-dynamodb-${ENV}"  \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"

deploy 04-cognito.yml       "${PROJECT_NAME}-cognito-${ENV}"   \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"        \
  CallbackUrl="${CALLBACK_URL}" LogoutUrl="${LOGOUT_URL}"

deploy 05-sqs.yml           "${PROJECT_NAME}-sqs-${ENV}"       \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"

deploy 06-s3.yml            "${PROJECT_NAME}-s3-${ENV}"        \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"

deploy 07-alb.yml           "${PROJECT_NAME}-alb-${ENV}"       \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"

deploy 08-ssm-parameter.yml "${PROJECT_NAME}-ssm-${ENV}"       \
  ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"        \
  AwsRegion="${REGION}"                                         \
  CallbackUrl="${CALLBACK_URL}" LogoutUrl="${LOGOUT_URL}"       \
  InputBucketExportName="${PROJECT_NAME}-s3-${ENV}-InputBucketName"   \
  OutputBucketExportName="${PROJECT_NAME}-s3-${ENV}-OutputBucketName" \
  SqsStackName="${PROJECT_NAME}-sqs-${ENV}"

# ECS is only deployed when image URIs are available (defined by GitHub Actions)
if [ -n "${API_IMAGE_URI}" ] && [ -n "${WORKER_IMAGE_URI}" ]; then
  deploy 09-ecs.yml "${PROJECT_NAME}-ecs-${ENV}"               \
    ProjectName="${PROJECT_NAME}" EnvironmentName="${ENV}"      \
    ApiImageUri="${API_IMAGE_URI}"                              \
    WorkerImageUri="${WORKER_IMAGE_URI}"
else
  echo ""
  echo "⚠ Skipping ECS: API_IMAGE_URI or WORKER_IMAGE_URI not set."
fi

echo ""
echo "✔ Deployment finished successfully."