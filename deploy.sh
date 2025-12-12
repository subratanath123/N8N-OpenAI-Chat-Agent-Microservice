#!/bin/bash

# Simple Docker deployment script - just pull and run
# Usage: ./deploy.sh [dev|qa|prod]
# Prerequisites: 
#   - Compose file must exist on deployment machine
#   - Docker image must be published to registry

set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================
readonly ENVIRONMENT="${1:-prod}"
readonly COMPOSE_FILE="docker-compose.${ENVIRONMENT}.yml"
readonly WORK_DIR="${PWD}"  # Use current working directory
readonly HEALTH_CHECK_TIMEOUT=60
readonly HEALTH_CHECK_INTERVAL=5

# ============================================================================
# Functions
# ============================================================================

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"
}

log_error() {
    echo "[ERROR] $*" >&2
}

log_success() {
    echo "[SUCCESS] $*"
}

validate_environment() {
    if [[ ! "$ENVIRONMENT" =~ ^(dev|qa|prod)$ ]]; then
        log_error "Invalid environment: $ENVIRONMENT. Must be dev, qa, or prod"
        exit 1
    fi
}

check_dependencies() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi
    
    if docker compose version &> /dev/null 2>&1; then
        readonly DOCKER_COMPOSE="docker compose"
    elif command -v docker-compose &> /dev/null; then
        readonly DOCKER_COMPOSE="docker-compose"
    else
        log_error "docker-compose is not installed"
        exit 1
    fi
}

check_compose_file() {
    local compose_path="${COMPOSE_FILE}"
    
    if [ ! -f "$compose_path" ]; then
        log_error "Compose file not found in current directory: $compose_path"
        log_error "Current directory: ${WORK_DIR}"
        exit 1
    fi
    
    log "Using compose file: ${WORK_DIR}/${compose_path}"
}

stop_existing_containers() {
    log "Stopping existing containers..."
    
    # Check if containers are managed by docker-compose
    if $DOCKER_COMPOSE -f "${COMPOSE_FILE}" ps -q | grep -q .; then
        $DOCKER_COMPOSE -f "${COMPOSE_FILE}" down \
            --remove-orphans \
            --timeout 30
        log_success "Containers stopped"
    else
        log "No containers found in docker-compose"
    fi
    
    # Also remove any existing containers with the same name (in case they were created manually)
    local container_name
    container_name=$(grep -E "^\s*container_name:" "${COMPOSE_FILE}" | head -1 | sed 's/.*container_name:[[:space:]]*//' | tr -d '"' | tr -d "'" || true)
    
    if [ -n "$container_name" ] && docker ps -a --format "{{.Names}}" | grep -q "^${container_name}$"; then
        log "Removing existing container: ${container_name}"
        docker rm -f "${container_name}" 2>/dev/null || true
        log_success "Existing container removed"
    fi
}

pull_images() {
    log "Checking for image updates..."
    
    # Try to pull images, but don't fail if image doesn't exist in registry
    # (it might be loaded locally from tar or built locally)
    set +e  # Temporarily disable exit on error
    local pull_output
    pull_output=$($DOCKER_COMPOSE -f "${COMPOSE_FILE}" pull 2>&1)
    local pull_exit_code=$?
    set -e  # Re-enable exit on error
    
    if [ $pull_exit_code -ne 0 ]; then
        if echo "$pull_output" | grep -qE "(Error|denied|not found|does not exist|pull access denied|repository does not exist)"; then
            log "Image may be loaded locally or not published to registry"
            log "Continuing with local images..."
            
            # Check if image exists locally
            local image_name
            image_name=$(grep -E "^[[:space:]]*image:" "${COMPOSE_FILE}" | head -1 | sed 's/.*image:[[:space:]]*//' | tr -d '"' | tr -d "'" || true)
            if [ -n "$image_name" ] && docker images --format "{{.Repository}}:{{.Tag}}" 2>/dev/null | grep -q "^${image_name}$"; then
                log_success "Found local image: ${image_name}"
            else
                log_warn "Image not found locally: ${image_name}"
                log "Container will be created when starting (if image is available)"
            fi
            return 0
        else
            log_error "Failed to pull images: $pull_output"
            exit 1
        fi
    fi
    
    log_success "Images checked/updated"
}

start_containers() {
    log "Starting containers..."
    
    local start_output
    start_output=$($DOCKER_COMPOSE -f "${COMPOSE_FILE}" up -d 2>&1) || {
        if echo "$start_output" | grep -qE "(no such image|image.*not found|cannot find image)"; then
            log_error "Docker image not found!"
            log_error "Please ensure the image is either:"
            log_error "  1. Published to a registry and accessible"
            log_error "  2. Loaded locally (docker load -i image.tar)"
            log_error "  3. Built locally (docker build -t jade-ai-bot:${ENVIRONMENT} .)"
            exit 1
        else
            log_error "Failed to start containers: $start_output"
            exit 1
        fi
    }
    
    log_success "Containers started"
}

verify_deployment() {
    log "Verifying deployment..."
    
    local elapsed=0
    while [ $elapsed -lt $HEALTH_CHECK_TIMEOUT ]; do
        if $DOCKER_COMPOSE -f "${COMPOSE_FILE}" ps | grep -q "Up"; then
            log_success "Containers are running"
            return 0
        fi
        sleep $HEALTH_CHECK_INTERVAL
        elapsed=$((elapsed + HEALTH_CHECK_INTERVAL))
        echo -n "."
    done
    echo ""
    
    log_error "Containers did not start within ${HEALTH_CHECK_TIMEOUT}s"
    return 1
}

show_status() {
    echo ""
    echo "=========================================="
    log_success "Deployment completed!"
    echo "  Environment: ${ENVIRONMENT}"
    echo "  Compose file: ${COMPOSE_FILE}"
    echo "  Working directory: ${WORK_DIR}"
    echo "=========================================="
    echo ""
    
    echo "Container status:"
    $DOCKER_COMPOSE -f "${COMPOSE_FILE}" ps
    echo ""
    
    echo "Useful commands:"
    echo "  View logs:    $DOCKER_COMPOSE -f ${COMPOSE_FILE} logs -f"
    echo "  Stop:         $DOCKER_COMPOSE -f ${COMPOSE_FILE} down"
    echo "  Restart:      $DOCKER_COMPOSE -f ${COMPOSE_FILE} restart"
    echo "  Status:       $DOCKER_COMPOSE -f ${COMPOSE_FILE} ps"
    echo ""
}

# ============================================================================
# Main Deployment Flow
# ============================================================================

main() {
    echo "=========================================="
    echo "Docker Deployment"
    echo "Environment: ${ENVIRONMENT}"
    echo "=========================================="
    echo ""
    
    validate_environment
    check_dependencies
    check_compose_file
    
    stop_existing_containers
    pull_images
    start_containers
    
    if verify_deployment; then
        show_status
        log_success "Deployment successful!"
    else
        log_error "Deployment verification failed"
        echo ""
        echo "Container logs:"
        $DOCKER_COMPOSE -f "${COMPOSE_FILE}" logs --tail 50
        exit 1
    fi
}

# Run main function
main "$@"
