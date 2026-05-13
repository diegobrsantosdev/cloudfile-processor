# ☁️ CloudFile Processor
 
A scalable, cloud-native file processing system built with **Java + Spring Boot** and deployed on **AWS**.  
Users upload files via pre-signed S3 URLs, which are processed asynchronously through a distributed pipeline using SQS, ECS, and DynamoDB — with all infrastructure managed as code via CloudFormation.
 
---
 
## Table of Contents
 
- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Processing Pipeline](#processing-pipeline)
- [API Endpoints](#api-endpoints)
- [API Testing](#api-testing)
- [Database Schema (Amazon DynamoDB)](#database-schema-amazon-dynamodb)
- [Infrastructure as Code (AWS)](#infrastructure-as-code-aws)
- [Observability & Quality](#observability--quality)
- [Security](#security)
- [Environment Variables (Systems Manager)](#environment-variables-systems-manager)
- [CI/CD & Automation](#cicd--automation)
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

## Tech Stack
 
### Backend
| Technology | Purpose |
|---|---|
| Java 21 | Core language |
| Spring Boot 3 | Application framework |
| Spring Security | Authentication & authorization |
| AWS SDK v2 | S3, SQS, DynamoDB, SSM and CloudWatch integration |
| DynamoDB Enhanced Client | Type-safe DynamoDB operations |
| AWS SSM | Centralized configuration and secret management |
| Amazon CloudWatch | Centralized logging and observability |
| JWT (Cognito) | Stateless authentication |
| JUnit 5 + Mockito | Unit and integration testing |
| Docker | Containerization |
| Maven | Build tool |
 
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
 
### File status lifecycle:
 
```
PENDING ──► PROCESSING ──► COMPLETED
                │
                └──► FAILED  (worker error, goes to DLQ)
 
COMPLETED or PENDING ──► DELETED  (user deletes file)
```

### Network topology
```
VPC: 10.0.0.0/16
├── Public Subnet A  (10.0.1.0/24)  ── ALB
├── Public Subnet B  (10.0.2.0/24)  ── ALB (multi-AZ)
├── Private Subnet A (10.0.3.0/24)  ── ECS API + Worker
└── Private Subnet B (10.0.4.0/24)  ── ECS API + Worker (multi-AZ)

ECS tasks run in **private subnets** — never exposed directly to the internet.
All inbound traffic flows through the ALB. Outbound internet access is via **NAT Gateway**.
 ```

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

### Admin Endpoints

| Method | Endpoint | Description | Response |
| :--- | :--- | :--- | :--- |
| `GET` | `/admin/users` | List all unique user IDs in the system | `200 List<String>` |
| `GET` | `/admin/users/{userId}` | List all files belonging to a specific user | `200 List<FileListResponse>` |
| `POST` | `/admin/{fileId}/reprocess` | Force a manual re-trigger of file processing | `200 OK` |
| `DELETE` | `/admin/{fileId}` | Administrative hard-delete of a specific file | `204 No Content` |
 
<details>
 
 <summary>📸 Click to view Postman evidences </summary>

![Generate presigned URL](assets/Captura%20de%20tela%202026-05-13%20184717.png)

![Send file](assets/Captura%20de%20tela%202026-05-13%20184736.png)
  
![List User Files](assets/Captura%20de%20tela%202026-05-12%20231607.png)

![List Users](assets/Captura%20de%20tela%202026-05-12%20231548.png)

</details>

---

## API Testing
The project includes complete Postman collections to facilitate testing of all endpoints, including authentication flows.

* [Download User API Collection](./assets/CloudFile-Processor%20%20%28User%20API%29.postman_collection.json)

* [Download Admin API Collection](./assets/CloudFile-Processor%20-%20Admin%20API.postman_collection.json)

> **Tip:** Import both the Collection file into Postman. Set the environment variables (like `base_url`) to match your deployment (Local or AWS) to start testing immediately.


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
 
---

## Database Schema (Amazon DynamoDB)

The system utilizes a **Single-Table Design** strategy to manage file metadata with high efficiency and low latency, optimized for per-user queries.

**Table Definition:**
- **Partition Key (PK):** `userId` (String) — Enables efficient per-user data isolation (Cognito `sub`).
- **Sort Key (SK):** `fileId` (String) — Unique identifier (UUID) for each file record.

| Attribute | Type | Description |
| :--- | :--- | :--- |
| `userId` | `String` | Unique identifier from Cognito (`sub` claim) |
| `fileId` | `String` | Unique UUID for the file record |
| `fileName` | `String` | Original name of the uploaded file |
| `s3Key` | `String` | S3 Path (e.g., `output/user18/file.pdf`) |
| `status` | `String` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `DELETED` |
| `sizeInBytes`| `Number` | File size for validation and metadata |
| `uploadDate` | `String` | ISO-8601 timestamp of creation |
| `updatedAt`  | `String` | ISO-8601 timestamp of last status change |

<details>
 
 <summary>📸 Click to view DynamoDB evidence </summary>
  
   ![Dynamo item table](assets/Captura%20de%20tela%202026-05-12%20225210.png)

</details>

---

### Example Item (JSON)
```json
{
  "userId": "user_12345",
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "monthly-report.pdf",
  "s3Key": "output/user_12345/550e8400-monthly-report.pdf",
  "status": "COMPLETED",
  "sizeInBytes": 10240,
  "uploadDate": "2026-03-26T14:30:00Z",
  "updatedAt": "2026-03-26T14:32:15Z"
}
```

---

## Infrastructure as Code (AWS)

The entire infrastructure is provisioned using **AWS CloudFormation**, following a modular stack approach for better maintainability and decoupled deployments.

| Component | Service | CloudFormation Stack | Role |
| :--- | :--- | :--- | :--- |
| **Network** | VPC | `network.yml` | Isolation with public/private subnets and NAT Gateway. |
| **Auth** | Cognito | `cognito.yml` | User Pool, App Client, and Hosted UI for JWT authentication. |
| **Storage** | S3 | `s3.yml` | Input and Output buckets with lifecycle and IAM policies. |
| **Database** | DynamoDB | `dynamodb.yml` | Single-table design for file metadata (userId PK + fileId SK). |
| **Messaging** | SQS | `sqs.yml` | Async queue with Dead Letter Queue (DLQ) for processing. |
| **Compute** | ECS Fargate | `ecs.yml` | Serverless container orchestration for API and Worker services. |
| **Traffic** | ALB | `alb.yml` | Application Load Balancer as the secure public entry point. |
| **Config** | SSM | `ssm.yml` | Centralized Parameter Store for environment variables. |
| **Registry** | ECR | *(Manual/CI)* | Private registry for Docker container images. |
| **Logging** | CloudWatch | *(Integrated)* | Centralized logs and performance monitoring. |

<details>

  <summary>📸 Click to view CloudFormation evidence </summary>
  
  ![Stacks do CloudFormation](assets/Captura%20de%20tela%202026-05-10%20171330.png)

</details>

---

### Deployment Order
```
To respect resource dependencies, the stacks must be deployed in this sequence:
`network` → `sqs` → `s3` → `dynamodb` → `cognito` → `ssm` → `alb` → `ecs`
```
 
---

## Observability & Quality

To ensure system reliability and maintainability, the following practices were implemented:

- **Logging & Tracing:** Centralized logging using **Amazon CloudWatch**. Application logs are formatted in JSON for structured analysis, with custom correlation IDs to trace requests across the API and Worker.
- **Monitoring:** CloudWatch Metrics and Alarms are used to monitor **SQS Queue depth** (to trigger scaling) and **ECS CPU/Memory** utilization.
- **Resilience:** - **Dead Letter Queues (DLQ):** Messages that fail after 3 retries are moved to a DLQ for manual inspection.
    - **Graceful Shutdown:** ECS tasks handle termination signals to complete processing before stopping.
- **Testing Strategy:** - **Unit Tests:** High coverage of business logic using **JUnit 5** and **Mockito**.
    - **Integration Tests:** Validation of S3 and DynamoDB interactions.
- **Error Handling:** Standardized API responses using `@ControllerAdvice` and custom exceptions (e.g., `OperationException`) to ensure clear error communication.

<details>
 
 <summary>📸 Click to view evidences </summary>
  
![CloudWatch Logs](assets/Captura%20de%20tela%202026-05-12%20224947.png)


![Exceptions](assets/Captura%20de%20tela%202026-05-12%20205238.png)


![Unit Tests](assets/Captura%20de%20tela%202026-05-12%20210018.png)

</details>
 
---

### Authentication (Cognito Hosted UI)
 
| Flow | URL |
|---|---|
| Login | `https://<cognito-domain>.auth.<region>.amazoncognito.com/login` |
| Token exchange | `POST /oauth2/token` |

<details>

 <summary>📸 Click to view Cognito evidences </summary>
  
![Exchange Token](assets/Captura%20de%20tela%202026-05-12%20214836.png)



![Cognito Users](assets/Captura%20de%20tela%202026-05-12%20230747.png)

</details>
 
## Security
 
- **Authentication:** AWS Cognito User Pool with JWT tokens (Bearer)
- **Authorisation:** User isolation enforced at service layer — every query is scoped to the authenticated `userId` extracted from the JWT `sub` claim
- **File access:** Files are never served directly — all downloads use time-limited pre-signed S3 URLs (15 min expiry)
- **Network:** ECS tasks run in private subnets, only reachable through the ALB
- **IAM:** ECS task role follows least-privilege — only the permissions each service actually needs
- **Passwords:** Managed entirely by Cognito — never stored in the application
- **Sensitive data:** Never exposed through API responses
 
---
 
## Environment Variables (Systems Manager)
 
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

<details>
  
 <summary>📸 Click to view all parameters evidences </summary>
  
![Parameter Store](assets/Captura%20de%20tela%202026-05-12%20230149.png)

</details>
 
---
 
## CI/CD & Automation

The entire lifecycle of this project is automated via **GitHub Actions**, from Docker image builds to infrastructure provisioning on AWS. This ensures a reliable and reproducible deployment process.

<details>
 
 <summary>📸 Click to view deployment & infrastructure evidence</summary>

  ### CI/CD Pipeline (GitHub Actions)
  The `deploy.yml` workflow manages the build process, pushes images to **Amazon ECR**, and triggers stack updates.
  
  ![Workflow de CI/CD](assets/Captura%20de%20tela%202026-05-10%20182528.png)
  > [🔗 View live execution on GitHub Actions](https://github.com/diegobrsantosdev/cloudfile-processor/actions/runs/25640656813)

### Resource Management (Cleanup)
To ensure cost efficiency and environment hygiene, I developed a custom automation script for a complete environment teardown. The script handles complex tasks such as recursively emptying S3 buckets and deleting CloudFormation stacks in the correct reverse-dependency order.

```bash
# Example usage of the automated cleanup script
./scripts/cleanup-infrastructure.sh --env dev
```
</details>
 
---
 
## Author
 
**Diego Santos**  
Backend Developer — Java · Spring Boot · AWS
 
- GitHub: [@diegobrsantosdev](https://github.com/diegobrsantosdev)
- LinkedIn: [diegobrsantos](https://linkedin.com/in/diegobrsantos)
- Email: diegobrsantosdev@gmail.com
