#!/bin/sh

# Destroys every stack of an environment, in reverse dependency order.
# The ECR stack is intentionally NOT deleted (it costs cents and keeps the Docker images).
#
# Usage: sh cleanup.sh dev            (asks for confirmation first)
#        sh cleanup.sh dev --yes      (no confirmation, e.g. for automation)

set -e

ENVIRONMENT=$1
CONFIRM=$2
PROJECT_NAME="file-processor"

if [ -z "$ENVIRONMENT" ]; then
  echo "Error: Environment not specified."
  exit 1
fi

S3_STACK="${PROJECT_NAME}-s3-${ENVIRONMENT}"
FRONTEND_STACK="${PROJECT_NAME}-frontend-${ENVIRONMENT}"

# Prints one output value of a stack (empty if the stack or the output does not exist).
stack_output() {
  value=$(aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue" \
    --output text 2>/dev/null) || value=""
  if [ "$value" = "None" ]; then
    value=""
  fi
  echo "$value"
}

# Deletes a stack and waits for it. With "set -e", the script stops here if the
# deletion fails, so later stacks are never touched in a half-broken state.
delete_stack() {
  echo "Deleting $1..."
  aws cloudformation delete-stack --stack-name "$2"
  aws cloudformation wait stack-delete-complete --stack-name "$2"
}

# CloudFormation can only delete a bucket when it is empty, including every
# object version and delete marker. Deletes in batches of up to 1000 keys.
empty_bucket() {
  bucket=$1
  if [ -z "$bucket" ]; then
    return 0
  fi
  if ! aws s3api head-bucket --bucket "$bucket" 2>/dev/null; then
    echo "  Bucket $bucket not found, skipping."
    return 0
  fi
  echo "  Emptying $bucket (all versions and delete markers)..."
  while :; do
    payload=$(aws s3api list-object-versions --bucket "$bucket" --max-items 1000 \
      --query '{Objects: [Versions, DeleteMarkers][][].{Key:Key,VersionId:VersionId}}' \
      --output json)
    # Nothing left to delete
    echo "$payload" | grep -q '"Key"' || break
    result=$(aws s3api delete-objects --bucket "$bucket" --delete "$payload" --output json)
    case "$result" in
      *'"Errors"'*)
        echo "Error: could not delete some objects from $bucket:" >&2
        echo "$result" >&2
        exit 1
        ;;
    esac
  done
  echo "  $bucket is empty."
}

# Buckets are read from the stack outputs, so only buckets that belong to these
# stacks can ever be emptied (no name guessing).
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
INPUT_BUCKET=$(stack_output "$S3_STACK" InputBucketName)
OUTPUT_BUCKET=$(stack_output "$S3_STACK" OutputBucketName)
FRONTEND_BUCKET=$(stack_output "$FRONTEND_STACK" FrontendBucketName)

echo "--------------------------------------------------------"
echo "STARTING INFRASTRUCTURE DESTRUCTION ($ENVIRONMENT)"
echo "--------------------------------------------------------"
echo "AWS account : ${ACCOUNT_ID}"
echo "Environment : ${ENVIRONMENT}"
echo "All stacks of this environment will be deleted (except ECR) and these"
echo "buckets will be PERMANENTLY emptied (users, files and data cannot be recovered):"
echo "  - ${INPUT_BUCKET:-<none: S3 stack not found>}"
echo "  - ${OUTPUT_BUCKET:-<none: S3 stack not found>}"
echo "  - ${FRONTEND_BUCKET:-<none: front-end stack not found>}"
echo "Note: deleting the CloudFront distribution can take 10-20 minutes."
echo "--------------------------------------------------------"

if [ "$CONFIRM" != "--yes" ]; then
  printf "Type '%s' to confirm: " "$ENVIRONMENT"
  read -r ANSWER
  if [ "$ANSWER" != "$ENVIRONMENT" ]; then
    echo "Aborted. Nothing was deleted."
    exit 1
  fi
fi

# 1. ECS (first, so nothing writes to the buckets or the queue anymore)
delete_stack "ECS" "${PROJECT_NAME}-ecs-${ENVIRONMENT}"

# 2. SSM
delete_stack "SSM" "${PROJECT_NAME}-ssm-${ENVIRONMENT}"

# 3. Front-end (CloudFront + bucket); must go before the ALB it points to
echo "Emptying front-end bucket..."
empty_bucket "$FRONTEND_BUCKET"
delete_stack "Front-end" "$FRONTEND_STACK"

# 4. ALB
delete_stack "ALB" "${PROJECT_NAME}-alb-${ENVIRONMENT}"

# 5. Cognito
delete_stack "Cognito" "${PROJECT_NAME}-cognito-${ENVIRONMENT}"

# 6. S3 (buckets must be emptied before the stack can be deleted)
echo "Emptying S3 buckets..."
empty_bucket "$INPUT_BUCKET"
empty_bucket "$OUTPUT_BUCKET"
delete_stack "S3" "$S3_STACK"

# 7. DynamoDB
delete_stack "DynamoDB" "${PROJECT_NAME}-dynamodb-${ENVIRONMENT}"

# 8. Network (VPC, subnets and Internet Gateway)
delete_stack "Network" "${PROJECT_NAME}-network-${ENVIRONMENT}"

# 9. SQS
delete_stack "SQS" "${PROJECT_NAME}-sqs-${ENVIRONMENT}"

echo "--------------------------------------------------------"
echo "SUCCESS: ALL STACKS REMOVED (ECR kept)."
echo "--------------------------------------------------------"