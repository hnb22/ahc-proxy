#!/bin/bash

# Initialize success counter
success_count=0
total_tests=7  
proxy_pid=""
backend_pids=() 

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "Starting comprehensive proxy functionality tests..."
echo "=================================================="

# Function to build classpath with dependencies
build_classpath() {
    echo -e "${BLUE}Building classpath with dependencies...${NC}"
    mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -q
    if [ -f "cp.txt" ]; then
        CLASSPATH=$(cat cp.txt):target/classes:target/ahc-proxy-1.0-SNAPSHOT.jar
        rm cp.txt
        echo -e "${GREEN}✅ Classpath built successfully${NC}"
    else
        echo -e "${RED}❌ Failed to build classpath${NC}"
        exit 1
    fi
}

# Function to start backend servers for cluster testing
start_backend_servers() {
    echo -e "${BLUE}Starting backend application servers for cluster testing...${NC}"
    
    for port in 8001 8002 8003; do
        echo "Starting backend application server on port $port..."
        java -cp "$CLASSPATH" -DlocalPort=$port -DserverName="server-$port" examples.TestBackendServer > /dev/null 2>&1 &
        local pid=$!
        backend_pids+=($pid)
        echo "Backend application server started on port $port with PID: $pid"
        
        # Wait a moment for the server to start
        sleep 2
        
        # Check if server is running
        if ! kill -0 $pid 2>/dev/null; then
            echo -e "${RED}❌ Failed to start backend server on port $port${NC}"
            return 1
        fi
        
        # Test if server is accepting connections
        timeout 3 bash -c "until nc -z localhost $port; do sleep 0.1; done" 2>/dev/null
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Backend application server on port $port is accepting connections${NC}"
        else
            echo -e "${RED}❌ Backend application server on port $port is not accepting connections${NC}"
            return 1
        fi
    done
    
    echo -e "${GREEN}✅ All backend application servers started successfully${NC}"
    return 0
}

# Function to stop backend servers
stop_backend_servers() {
    if [ ${#backend_pids[@]} -gt 0 ]; then
        echo -e "${YELLOW}Stopping backend application servers...${NC}"
        for pid in "${backend_pids[@]}"; do
            if [ ! -z "$pid" ] && kill -0 $pid 2>/dev/null; then
                echo "Stopping backend application server (PID: $pid)..."
                kill $pid
                sleep 1
                
                # Force kill if still running
                if kill -0 $pid 2>/dev/null; then
                    echo "Force killing backend application server (PID: $pid)..."
                    kill -9 $pid
                fi
            fi
        done
        backend_pids=()
        echo -e "${GREEN}✅ All backend application servers stopped${NC}"
    fi
}

# Function to start non-cluster proxy server
start_non_cluster_proxy() {
    echo -e "${BLUE}Starting non-cluster proxy server...${NC}"
    java -cp "$CLASSPATH" examples.TestHttp1 &
    proxy_pid=$!
    echo "Proxy server started with PID: $proxy_pid"
    
    # Wait for server to start
    echo "Waiting for proxy server to initialize..."
    sleep 3
    
    # Check if server is running and responding
    if ! kill -0 $proxy_pid 2>/dev/null; then
        echo -e "${RED}❌ Failed to start non-cluster proxy server${NC}"
        return 1
    fi
    
    # Test if server is accepting connections
    timeout 5 bash -c 'until nc -z localhost 8000; do sleep 0.1; done' 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Non-cluster proxy server is running and accepting connections${NC}"
        return 0
    else
        echo -e "${RED}❌ Non-cluster proxy server is not accepting connections${NC}"
        stop_proxy
        return 1
    fi
}

# Function to start cluster proxy server
start_notifier_log_proxy() {
    echo -e "${BLUE}Starting Notifier log proxy server...${NC}"
    java -cp "$CLASSPATH" examples.TestHttp1Notifier &
    proxy_pid=$!
    echo "Notifer log proxy server started with PID: $proxy_pid"
    
    # Wait for server to start
    echo "Waiting for Notifier log proxy server to initialize..."
    sleep 3
    
    # Check if server is running and responding
    if ! kill -0 $proxy_pid 2>/dev/null; then
        echo -e "${RED}❌ Failed to start Notifer log proxy server${NC}"
        return 1
    fi
    
    # Test if server is accepting connections
    timeout 5 bash -c 'until nc -z localhost 8000; do sleep 0.1; done' 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Notifer log proxy server is running and accepting connections${NC}"
        return 0
    else
        echo -e "${RED}❌ Notifer log proxy server is not accepting connections${NC}"
        stop_proxy
        return 1
    fi
}

# Function to stop proxy server
stop_proxy() {
    if [ ! -z "$proxy_pid" ] && kill -0 $proxy_pid 2>/dev/null; then
        echo -e "${YELLOW}Stopping proxy server (PID: $proxy_pid)...${NC}"
        kill $proxy_pid
        sleep 2
        
        # Force kill if still running
        if kill -0 $proxy_pid 2>/dev/null; then
            echo -e "${YELLOW}Force killing proxy server...${NC}"
            kill -9 $proxy_pid
        fi
        
        echo -e "${GREEN}✅ Proxy server stopped${NC}"
    fi
    proxy_pid=""
}

# Function to run proxy tests
run_proxy_tests() {
    local test_type=$1
    echo
    echo -e "${BLUE}Running tests for $test_type proxy...${NC}"
    echo "================================="
    
    # GET operation (no auth, compression)
    echo "Test 1: GET operation (no auth, no compression)"
    if timeout 10 curl -x localhost:8000 "http://echo.free.beeceptor.com/sample-request?author=beeceptor" -s -o /dev/null -w "%{http_code}" | grep -q "200"; then
        echo -e "${GREEN}✅ GET test passed${NC}"
        ((success_count++))
    else
        echo -e "${RED}❌ GET test failed${NC}"
    fi
    echo

    # POST operation (body, header)
    echo "Test 2: POST operation (with body and headers)"
    if timeout 10 curl -x localhost:8000 -X POST -H "Content-Type: application/json" -d '{"name": "John Doe", "age": 30, "city": "New York"}' "http://echo.free.beeceptor.com/sample-request?author=beeceptor" -s -o /dev/null -w "%{http_code}" | grep -q "200"; then
        echo -e "${GREEN}✅ POST test passed${NC}"
        ((success_count++))
    else
        echo -e "${RED}❌ POST test failed${NC}"
    fi
    echo

    # POST operation (body, header, w/compression)
    echo "Test 3: POST operation (with compression)"
    if timeout 10 curl -x localhost:8000 -X POST -H "Content-Type: application/json" -d '{"name": "John Doe", "age": 30, "city": "New York"}' "http://echo.free.beeceptor.com/sample-request?author=beeceptor" -H "Accept-Encoding: gzip" -s -o /dev/null -w "%{http_code}" | grep -q "200"; then
        echo -e "${GREEN}✅ POST with compression test passed${NC}"
        ((success_count++))
    else
        echo -e "${RED}❌ POST compression test failed${NC}"
    fi
    echo

    # HTTPS tunneling test (only for non-cluster proxy)
    if [ "$test_type" = "non-cluster" ]; then
        echo "Test 4: HTTPS tunneling"
        if timeout 10 curl -x localhost:8000 "https://echo.free.beeceptor.com/sample-request?author=beeceptor" -s -o /dev/null -w "%{http_code}" | grep -q "200"; then
            echo -e "${GREEN}✅ Tunnel successfully worked. Test passed${NC}"
            ((success_count++))
        else
            echo -e "${RED}❌ Tunnel test failed${NC}"
        fi
        echo
    else
        echo "Test 4: HTTPS tunneling"
        echo -e "${YELLOW}⚠️ HTTPS tunneling is not supported in cluster mode - SKIPPED${NC}"
        echo
    fi
}

# Cleanup function for graceful exit
cleanup() {
    echo -e "${YELLOW}Cleaning up...${NC}"
    stop_proxy
    stop_backend_servers
    exit 0
}

# Set trap for cleanup on script exit
trap cleanup EXIT INT TERM

# Always recompile the project to ensure latest code changes
echo -e "${YELLOW}Compiling project...${NC}"
mvn clean compile package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to compile project${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Project compiled successfully${NC}"

# Build classpath with all dependencies
build_classpath

echo -e "${BLUE}Testing Non-Cluster Proxy Server${NC}"
echo "=================================="

# Test non-cluster proxy
if start_non_cluster_proxy; then
    run_proxy_tests "non-cluster"
else
    echo -e "${RED}❌ Failed to start non-cluster proxy server, skipping tests${NC}"
fi

# Stop the non-cluster proxy
stop_proxy
sleep 2

echo
echo -e "${BLUE}Testing Notifier log Proxy Server${NC}"
echo "============================="

# Start backend servers for cluster testing
if start_backend_servers; then
    # Test Notifier log proxy
    if start_notifier_log_proxy; then
        run_proxy_tests "cluster"
    else
        echo -e "${RED}❌ Failed to start cluster proxy server, skipping tests${NC}"
    fi
    
    # Stop the cluster proxy
    stop_proxy
    
    # Stop backend application servers
    stop_backend_servers
else
    echo -e "${RED}❌ Failed to start backend application servers, skipping cluster tests${NC}"
fi

echo
echo -e "${YELLOW}ℹ️  Logger notifier mode tests are commented out (implementation not finished)${NC}"

# Final results
echo
echo "=================================================="
echo -e "${BLUE}Final Test Results: $success_count/$total_tests tests passed${NC}"

if [ $success_count -eq $total_tests ]; then
    echo -e "${GREEN}🎉 SUCCESS: All proxy functionality tests completed successfully!${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠️  Some tests failed ($((total_tests - success_count)) failures). Please check the logs and try again.${NC}"
    exit 1
fi
