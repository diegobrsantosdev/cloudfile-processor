# AWS Services Overview

| AWS Service       | Role in the System                                   | Why Use It |
|------------------|------------------------------------------------------|------------|
| Amazon Cognito    | User authentication                                 | Enables secure login without manual implementation |
| Amazon S3         | File storage                                        | High durability, scalable, supports large uploads |
| Amazon SQS        | Message queue                                       | Decouples upload from asynchronous processing |
| Amazon ECS        | Container execution for processing                  | Ideal for heavy tasks that cannot run in Lambda |
| Amazon DynamoDB   | Stores status and metadata                           | Highly scalable NoSQL database |
| CloudFormation    | Infrastructure as code                               | Allows creation and versioning of entire infrastructure |
| Amazon ECR        | Docker image registry                                | Required for ECS Fargate containers |
| CloudWatch        | Logging and monitoring                               | Observability and troubleshooting |