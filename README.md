# AHC Proxy - Work in Progress

HTTP/HTTPS proxy server built with Netty featuring modular pipeline architecture, multi-protocol support (HTTP/1.1, HTTP/2, WebSocket), and pluggable processing stages for auth, caching, and compression.

## 🚀 Features

- **Multi-Protocol Support**: HTTP/1.1, HTTP/2, WebSocket
- **Modular Pipeline Architecture**: Pluggable stages for authentication, caching, compression
- **Asynchronous Connection Handling**: Non-blocking I/O with Netty
- **Smart Routing**: Intelligent backend targeting with metadata-driven routing
- **Auto Port Detection**: Automatic port resolution based on URI schemes
- **Production Ready**: Comprehensive error handling, graceful shutdown, memory management
- **Extensible Design**: Clean OOP abstraction over Netty for easy customization

## 📋 Prerequisites

- **Java 11** or higher
- **Maven 3.6+**
- **Network connectivity** for testing with external targets

## 🛠️ Quick Setup

### 1. Clone the Repository
```bash
git clone https://github.com/hnb22/ahc-proxy.git
cd ahc-proxy
```

### 2. Build the Project
```bash
# Clean and compile
mvn clean compile

# Or build with tests
mvn clean install
```

### 3. Run the Proxy Server
```bash
# Start with default port (8000)
java -cp target/classes com.example.proxy.ApplicationHttp1

# Or specify custom port
java -DlocalPort=8080 -cp target/classes com.example.proxy.ApplicationHttp1
```

You should see:
```
Proxy server started on port 8000 with protocol: HTTP1
Proxy server is running. Press Ctrl+C to stop.
```

## 🧪 Testing the Proxy

### Current Basic HTTP Proxy Test
```bash
# Test with a local HTTP server
python3 -m http.server 8080  # In another terminal

# Use the proxy
curl -x localhost:8000 http://localhost:8080/
```
