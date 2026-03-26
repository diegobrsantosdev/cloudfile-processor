# ☁️ CloudFile Processor
 
A scalable, cloud-native file processing system built with **Java + Spring Boot** and deployed on **AWS**.  
Users upload files via pre-signed S3 URLs, which are processed asynchronously through a distributed pipeline using SQS, ECS, and DynamoDB — with all infrastructure managed as code via CloudFormation.
 
---
 
## Table of Contents
 
- [Overview](#overview)
- [Architecture](#architecture)
- [Processing Pipeline](#processing-pipeline)
- [Tech Stack](#tech-stack)
- [Infrastructure](#infrastructure)
- [API Endpoints](#api-endpoints)
- [Security](#security)
- [Environment Variables](#environment-variables)
- [CloudFormation Stacks](#cloudformation-stacks)
- [Author](#author)
 
---
 
## Overview
 
CloudFile Processor solves the challenge of handling file uploads reliably at scale. Instead of uploading files directly through the API (which creates bottlenecks and timeouts), the system uses **pre-signed S3 URLs** — the client uploads directly to S3, and a background worker processes the file asynchronously.
 
**Key design decisions:**
- Files never pass through the API server — direct client-to-S3 upload via pre-signed URLs
- Processing is decoupled via SQS — the API responds instantly, processing happens in the background
- All infrastructure is reproducible via CloudFormation — no manual AWS console setup
- Metadata is tracked in DynamoDB with full status history (PENDING → PROCESSING → COMPLETED / FAILED / DELETED)
 
---
 
## Architecture
 
```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT (Postman / Frontend)                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTP
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Application Load Balancer (ALB)                  │
│                        internet-facing · HTTP:80                    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│              ECS Fargate — API Service (Private Subnet)             │
│                     Spring Boot · Port 8080                         │
│                                                                     │
│   POST /files ──────────────────────────────────────► S3 Input      │
│   (returns pre-signed URL)                           Bucket         │
│                                                                     │
│   GET  /files          ◄──── DynamoDB ────────────────────────────  │
│   GET  /files/{id}     ◄──── DynamoDB + S3 pre-signed URL          │
│   GET  /files/history  ◄──── DynamoDB                              │
│   DELETE /files/{id}   ──────────────────────────────► S3 + Dynamo  │
└─────────────────────────────────────────────────────────────────────┘
                             
┌─────────────────────────────────────────────────────────────────────┐
│                         AWS S3 Input Bucket                         │
│              s3:ObjectCreated → triggers SQS message                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Amazon SQS Queue                               │
│              VisibilityTimeout: 300s · DLQ after 3 retries         │
└────────────────────────────┬────────────────────────────────────────┘
                             │ polls every 5s (long polling)
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│             ECS Fargate — Worker Service (Private Subnet)           │
│                    FileProcessingWorker (@Scheduled)                │
│                                                                     │
│   1. Reads SQS message                                              │
│   2. Updates DynamoDB → PROCESSING                                  │
│   3. Copies file: input/ ──────────────────────► output/            │
│   4. Deletes from input bucket                                      │
│   5. Updates DynamoDB → COMPLETED + new bucket + new s3Key         │
│   6. Deletes SQS message                                            │
└─────────────────────────────────────────────────────────────────────┘
```
 
---
 
## Processing Pipeline
 
```
User uploads file
      │
      ▼
POST /api/v1/files
      │
      ├── generates uploadId (UUID)
      ├── builds s3Key: input/{userId}/{uploadId}-{fileName}
      ├── generates pre-signed PUT URL (15 min expiry)
      ├── saves metadata to DynamoDB  ──► status: PENDING
      └── returns pre-signed URL to client
      
      │ (client uploads directly to S3)
      ▼
      
S3 ObjectCreated event
      │
      └──► SQS message enqueued
      
      │ (worker polls SQS every 5s)
      ▼
      
Worker receives message
      │
      ├── updates DynamoDB  ──────────────────────────► status: PROCESSING
      ├── copies file to output bucket
      ├── deletes from input bucket
      ├── updates DynamoDB  ──────────────────────────► status: COMPLETED
      │                                                  s3Key: output/...
      │                                                  bucket: output-bucket
      └── deletes SQS message
      
      │ (on failure: message returns to queue, max 3 retries → DLQ)
```
 
**File status lifecycle:**
 
```
PENDING ──► PROCESSING ──► COMPLETED
                │
                └──► FAILED  (worker error, goes to DLQ)
 
COMPLETED or PENDING ──► DELETED  (user deletes file)
```
 
---
 
## Tech Stack
 
### Backend
| Technology | Purpose |
|---|---|
| Java 17 | Core language |
| Spring Boot 3 | Application framework |
| Spring Security | Authentication & authorization |
| Spring Data JPA | (reserved for relational data) |
| AWS SDK v2 | S3, SQS, DynamoDB integration |
| DynamoDB Enhanced Client | Type-safe DynamoDB operations |
| JWT (Cognito) | Stateless authentication |
| JUnit 5 + Mockito | Unit and integration testing |
| Docker | Containerisation |
| Maven | Build tool |
 
### AWS Infrastructure
| Service | Role |
|---|---|
| Amazon S3 | File storage (input + output buckets) |
| Amazon SQS | Async message queue with DLQ |
| Amazon DynamoDB | File metadata storage |
| Amazon ECS Fargate | Serverless container orchestration |
| Amazon ECR | Container image registry |
| Amazon Cognito | User authentication (User Pool + JWT) |
| Application Load Balancer | Public API entry point |
| AWS CloudFormation | Infrastructure as Code |
| AWS SSM Parameter Store | Centralised configuration |
| Amazon CloudWatch | Logs and monitoring |
| Amazon VPC | Network isolation (public + private subnets) |
| NAT Gateway | Outbound internet for private subnets |
 
---
 
## Infrastructure
 
The infrastructure is fully managed with CloudFormation, split into independent stacks:
 
```
cloudformation/
├── network.yml       # VPC, subnets, IGW, NAT Gateway, route tables
├── sqs.yml           # SQS queue, DLQ, queue policy
├── s3.yml            # Input/output buckets, IAM role
├── dynamodb.yml      # Files table (userId PK + fileId SK)
├── cognito.yml       # User Pool, App Client, Hosted UI domain
├── ssm.yml           # All SSM parameters (bucket names, queue URL, etc.)
├── alb.yml           # Application Load Balancer, Target Group, Listener
└── ecs.yml           # ECS Cluster, Task Definitions, Services (API + Worker)
```
 
**Deployment order:**
```
network → sqs → s3 → dynamodb → cognito → ssm → alb → ecs
```
 
### Network topology
```
VPC: 10.0.0.0/16
├── Public Subnet A  (10.0.1.0/24)  ── ALB
├── Public Subnet B  (10.0.2.0/24)  ── ALB (multi-AZ)
├── Private Subnet A (10.0.3.0/24)  ── ECS API + Worker
└── Private Subnet B (10.0.4.0/24)  ── ECS API + Worker (multi-AZ)
```
 
ECS tasks run in **private subnets** — never exposed directly to the internet. All inbound traffic flows through the ALB. Outbound internet access is via **NAT Gateway**.
 
---
 
## API Endpoints
 
Base URL: `http://<alb-dns>/api/v1`
 
> Authentication: **Bearer Token** (AWS Cognito JWT) — all endpoints require a valid token.
 
### Files
 
| Method | Endpoint | Description | Response |
|---|---|---|---|
| `POST` | `/files` | Request a pre-signed upload URL | `200 FileUploadResponse` |
| `GET` | `/files` | List active files for authenticated user | `200 List<FileListResponse>` |
| `GET` | `/files/history` | Full file history including deleted | `200 List<FileListResponse>` |
| `GET` | `/files/{fileId}` | Get pre-signed download URL for a file | `200 FileDownloadResponse` |
| `DELETE` | `/files/{fileId}` | Soft-delete in DynamoDB + hard-delete in S3 | `204 No Content` |
 
### Authentication (Cognito Hosted UI)
 
| Flow | URL |
|---|---|
| Login | `https://<cognito-domain>.auth.<region>.amazoncognito.com/login` |
| Token exchange | `POST /oauth2/token` |
 
---
 
### Request & Response Examples
 
**POST `/api/v1/files`**
```json
// Request
{
  "originalFileName": "relatorio-marco.pdf",
  "mimeType": "application/pdf",
  "sizeInBytes": 10240
}
 
// Response 200
{
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "s3Key": "input/user18/550e8400-relatorio-marco.pdf",
  "preSignedUrl": "https://s3.amazonaws.com/...",
  "expiresAt": "2026-03-26T15:30:00Z",
  "status": "PENDING"
}
```
 
**GET `/api/v1/files`**
```json
[
  {
    "fileId": "550e8400-e29b-41d4-a716-446655440000",
    "s3Key": "output/user18/550e8400-relatorio-marco.pdf",
    "fileName": "relatorio-marco.pdf",
    "mimeType": "application/pdf",
    "sizeInBytes": 10240,
    "uploadDate": "2026-03-26T14:30:00Z",
    "status": "COMPLETED"
  }
]
```
 
**GET `/api/v1/files/{fileId}`**
```json
{
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "relatorio-marco.pdf",
  "preSignedUrl": "https://s3.amazonaws.com/..."
}
```
 
---
 
## Security
 
- **Authentication:** AWS Cognito User Pool with JWT tokens (Bearer)
- **Authorisation:** User isolation enforced at service layer — every query is scoped to the authenticated `userId` extracted from the JWT `sub` claim
- **File access:** Files are never served directly — all downloads use time-limited pre-signed S3 URLs (15 min expiry)
- **Network:** ECS tasks run in private subnets, only reachable through the ALB
- **IAM:** ECS task role follows least-privilege — only the permissions each service actually needs
- **Passwords:** Managed entirely by Cognito — never stored in the application
- **Sensitive data:** Never exposed through API responses
 
---

 
> The application uses AWS SSM Parameter Store for configuration. Make sure your environment has valid AWS credentials with `ssm:GetParameter` permissions.
 
---
 
## Environment Variables
 
All configuration is loaded from **AWS SSM Parameter Store** at startup. The parameters follow the pattern `/{project}/{environment}/{service}/{key}`.
 
| SSM Parameter | Description |
|---|---|
| `/file-processor/dev/aws/region` | AWS region |
| `/file-processor/dev/s3/input-bucket-name` | S3 input bucket |
| `/file-processor/dev/s3/output-bucket-name` | S3 output bucket |
| `/file-processor/dev/s3/presigned-url-expiration-minutes` | Pre-signed URL TTL |
| `/file-processor/dev/sqs/queue-url` | SQS queue URL |
| `/file-processor/dev/dynamodb/files-table-name` | DynamoDB table name |
| `/file-processor/dev/cognito/callback-url` | Cognito callback URL |
| `/file-processor/dev/cognito/logout-url` | Cognito logout URL |
 
---
 
## CloudFormation Stacks
 
Deploy in order:
 
```bash
# 1. Network
aws cloudformation deploy \
  --template-file cloudformation/network.yml \
  --stack-name file-processor-network-dev
 
# 2. SQS
aws cloudformation deploy \
  --template-file cloudformation/sqs.yml \
  --stack-name file-processor-sqs-dev \
  --parameter-overrides InputBucketArn=<input-bucket-arn>
 
# 3. S3
aws cloudformation deploy \
  --template-file cloudformation/s3.yml \
  --stack-name file-processor-s3-dev \
  --capabilities CAPABILITY_NAMED_IAM
 
# 4. DynamoDB
aws cloudformation deploy \
  --template-file cloudformation/dynamodb.yml \
  --stack-name file-processor-dynamodb-dev
 
# 5. Cognito
aws cloudformation deploy \
  --template-file cloudformation/cognito.yml \
  --stack-name file-processor-cognito-dev
 
# 6. SSM
aws cloudformation deploy \
  --template-file cloudformation/ssm.yml \
  --stack-name file-processor-ssm-dev \
  --parameter-overrides \
    InputBucketExportName=file-processor-s3-dev-InputBucketName \
    OutputBucketExportName=file-processor-s3-dev-OutputBucketName
 
# 7. ALB
aws cloudformation deploy \
  --template-file cloudformation/alb.yml \
  --stack-name file-processor-alb-dev
 
# 8. ECS
aws cloudformation deploy \
  --template-file cloudformation/ecs.yml \
  --stack-name file-processor-ecs-dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    ApiImageUri=<ecr-api-image-uri> \
    WorkerImageUri=<ecr-worker-image-uri>
```
 
---
 
## Author
 
**Diego Santos**  
Backend Developer — Java · Spring Boot · AWS
 
- GitHub: [@diegobrsantosdev](https://github.com/diegobrsantosdev)
- LinkedIn: [diegobrsantos](https://linkedin.com/in/diegobrsantos)
- Email: diegobrsantosdev@gmail.com
