# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

EatCloud MSA v2 is a Spring Boot microservices architecture for a food delivery platform built with Java 21 and Spring Cloud. The system uses Netflix Eureka for service discovery, PostgreSQL (PostGIS) for data persistence, Redis for caching and distributed locking, and implements distributed transactions using the Saga pattern with Redisson.

## Development Commands

### Quick Start
```bash
# Development environment with Docker Compose
./run-dev.sh
# or
docker-compose up

# Production environment
./run-prod.sh
# or 
docker-compose -f deploy/compose/.yml -f deploy/compose/prod/.yml up -d --build
```

### Build System (Gradle)
```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :order-service:build

# Run all tests
./gradlew test

# Run tests for specific service
./gradlew :order-service:test

# Run integration tests (where available)
./gradlew :order-service:integrationTest

# Clean build
./gradlew clean build

# Start a specific service locally (from service directory)
cd order-service && ./gradlew bootRun
```

### Docker Operations
```bash
# Build and start all services
docker-compose up --build

# Start services in background
docker-compose up -d

# View logs for specific service
docker-compose logs -f order-service

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v

# Check service status
docker-compose ps
```

### Database Operations
```bash
# Connect to local PostgreSQL (development)
docker exec -it eatcloud-db psql -U eatcloud_user -d eatcloud_db

# List databases
\l

# Connect to specific service database
\c order_db

# View tables in current database
\dt

# Exit PostgreSQL
\q
```

### Individual Service Operations
```bash
# Start only essential services (Eureka + Gateway + Database)
docker-compose up eureka-server api-gateway eatcloud-db redis

# Access service-specific gradle wrapper
cd [service-name] && ./gradlew bootRun

# Run service with specific profile
cd order-service && ./gradlew bootRun -Dspring.profiles.active=dev
```

## Architecture Overview

### Microservices Structure
- **eureka-server** (8761): Netflix Eureka service discovery server
- **api-gateway** (8080): Spring Cloud Gateway for request routing and load balancing  
- **auth-service** (8081): Authentication and authorization service
- **customer-service** (8082): Customer management and profiles
- **admin-service** (8083): Administrative operations
- **manager-service** (8084): Store manager operations  
- **store-service** (8085): Store and menu management
- **order-service** (8086): Order processing with Saga pattern and distributed transactions
- **payment-service** (8087): Payment processing
- **auto-time**: Automated time-based operations
- **auto-response**: Automated response handling

### Key Technologies
- **Java 21**: Language runtime with modern features
- **Spring Boot 3.5.3**: Application framework
- **Spring Cloud 2025.0.0**: Microservices toolkit (Gateway, Netflix Eureka, LoadBalancer)
- **PostgreSQL with PostGIS**: Primary database with spatial extensions  
- **Redis**: Caching and distributed locking (via Redisson)
- **Redisson**: Advanced Redis client for distributed systems
- **Kafka**: Event streaming platform for async communication
- **Docker**: Containerization for all services

### Data Architecture
All services share a single PostgreSQL instance with separate databases:
- `auth_db` - Authentication data
- `customer_db` - Customer information  
- `admin_db` - Administrative data
- `manager_db` - Store manager data
- `store_db` - Store and menu data
- `order_db` - Orders and transactions (most complex)
- `payment_db` - Payment records

### Distributed Transaction Handling

The order-service implements sophisticated distributed transaction management:

**Saga Pattern Implementation**: Uses orchestrator-based Saga for cross-service transactions, particularly for cart-to-order conversion with automatic compensation.

**Redisson Distributed Locking**: 
- Multi-lock capability for atomic operations across multiple resources
- Fair locks to prevent starvation
- Watchdog mechanism for automatic lock renewal during long transactions
- Used primarily for cart operations and order creation

**Key Transaction Flow (Order Creation)**:
1. Acquire distributed locks (customer, store, cart)
2. Validate cart contents and inventory
3. Reserve inventory via store-service 
4. Optionally deduct customer points
5. Create order record
6. Clear cart
7. Release locks with automatic compensation on any failure

### Service Communication
- **Synchronous**: OpenFeign clients with circuit breakers for service-to-service calls
- **Asynchronous**: Kafka for event-driven communication
- **Service Discovery**: All services register with Eureka server
- **Load Balancing**: Spring Cloud LoadBalancer for client-side load balancing

### Configuration Management
- Environment-specific properties files (`application-{profile}.properties`)
- Docker environment variable injection
- Separate configuration for development (`dev`) and production (`prod`) profiles

## Development Environment Setup

### Prerequisites
- Java 21 (required for all services)
- Docker and Docker Compose
- PostgreSQL client tools (optional, for database access)

### Service Ports (Development)
- Eureka Server: http://localhost:8761
- API Gateway: http://localhost:8080  
- Auth Service: http://localhost:8081
- Customer Service: http://localhost:8082
- Admin Service: http://localhost:8083
- Manager Service: http://localhost:8084
- Store Service: http://localhost:8085
- Order Service: http://localhost:8086
- Payment Service: http://localhost:8087
- PostgreSQL: localhost:5432
- Redis: localhost:6379

### Key Development Notes

**Order Service Complexity**: The order-service is the most sophisticated service, implementing:
- Distributed locking with Redisson
- Saga pattern for distributed transactions
- Shopping cart management
- Review system
- Circuit breaker patterns

**Service Maturity**: Based on the run scripts, the order-service appears to be the most complete implementation, while other services may be in various stages of development.

**Database Migration**: The system migrated from multiple databases to a single PostgreSQL instance with multiple schemas for simplified development and deployment.

**Testing Strategy**: Each service has its own test suite. The order-service includes both unit and integration tests with comprehensive transaction testing.

## Kubernetes Deployment

### Local Development with Minikube
```bash
# Start Minikube cluster
minikube start --driver=docker --cpus=4 --memory=7g --disk-size=50g

# Deploy all services to Kubernetes
./deploy-k8s-local.sh

# Access services via port forwarding
kubectl port-forward -n eatcloud service/api-gateway 8080:8080

# Check deployment status
kubectl get all -n eatcloud

# View service logs
kubectl logs -f deployment/order-service -n eatcloud

# Scale services
kubectl scale deployment order-service --replicas=3 -n eatcloud

# Clean up
kubectl delete namespace eatcloud
```

### Kubernetes Architecture
- **Namespace**: `eatcloud` for resource isolation
- **ConfigMaps**: Environment-specific configuration management
- **Secrets**: Secure handling of database passwords and JWT secrets
- **StatefulSets**: PostgreSQL with persistent storage
- **Deployments**: Microservices with health checks and resource limits
- **Services**: Internal service discovery and load balancing
- **LoadBalancer**: External access via API Gateway

### AWS EKS Deployment
```bash
# Create EKS cluster (requires AWS CLI and eksctl)
eksctl create cluster --name eatcloud-cluster --region us-west-2 --nodes 3

# Configure kubectl for EKS
aws eks update-kubeconfig --region us-west-2 --name eatcloud-cluster

# Deploy to EKS (modify image references to ECR)
kubectl apply -f k8s/

# Set up ingress controller for external access
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/aws/deploy.yaml
```

### Key Kubernetes Features
- **Health Checks**: Liveness and readiness probes for all services
- **Resource Management**: CPU and memory limits/requests
- **Service Discovery**: Kubernetes native service discovery
- **Auto-scaling**: Horizontal Pod Autoscaler ready
- **Persistent Storage**: StatefulSets for databases with PVC
- **Configuration Management**: Externalized config via ConfigMaps
- **Security**: Pod security policies and network policies ready

## Environment Configuration

The project supports multiple deployment environments:
- **Docker Compose**: Traditional container orchestration via `deploy/compose/` overlays
- **Kubernetes**: Cloud-native deployment with `k8s/` manifests
- **Minikube**: Local Kubernetes development environment
- **AWS EKS**: Production Kubernetes in the cloud
