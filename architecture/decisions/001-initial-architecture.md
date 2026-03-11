# 001 - Initial Architecture

## Context
Project: Cloudfile Processor  
Goal: Scalable system for file upload and processing using AWS

## Main Components
- **Frontend / Client**: performs login and uploads files
- **Amazon Cognito**: user authentication
- **Backend API (Spring Boot)**: generates Pre Signed URL and coordinates workflow
- **Amazon S3**: stores original and processed files
- **Amazon SQS**: asynchronous processing queue
- **Amazon ECS (Fargate)**: runs processing containers
- **Amazon DynamoDB**: stores processing status and metadata
- **CloudWatch**: monitoring and logging
- **CloudFormation**: manages all infrastructure as code
- **Amazon ECR**: stores Docker images used by ECS

## Design Decisions
- Use ECS containers to process large files (>5GB)
- Use S3 + SQS for event-driven and decoupled architecture
- Use DynamoDB for status and metadata (scalable NoSQL)
- Use CloudFormation to version infrastructure