# AHC Proxy Server

A HTTP/HTTPS proxy server built with Java and Netty, featuring a CLI tool and clustering capabilities for request distribution and response aggregation.

## Installation

1. **Clone the repository:**
```bash
git clone <repository-url>
cd ahc-proxy
```

2. **Build the project:**
```bash
mvn clean install
```

## Usage

### CLI Tool

The proxy server includes a CLI tool for easy management:

#### Still needs implementation

### Starting the Proxy Server

#### Regular Proxy Mode
```java
package examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.config.ProtocolConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.exceptions.ProxyException;

public class TestHttp1 {

    private static final Logger logger = LoggerFactory.getLogger(TestHttp1.class);
    
    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));
    static final String LOCAL_HOST = "localhost";

    public static void main(String[] args) throws ProxyException {
        ProxyServer proxy = new ProxyServer(new ProtocolConfig("HTTP1", LOCAL_PORT, false));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_HOST, null));
            proxy.start();
            
            logger.info("Proxy server is running. Press Ctrl+C to stop.");
            
            proxy.sync();
            
        } catch (ProxyException e) {
            logger.error("Failed to start proxy server: {}", e.getMessage());
            proxy.stop();
        } finally {
            logger.info("Proxy server is shutting down");
            proxy.stop();
        }
    }
}
```

#### Cluster Mode
```java
// Start cluster proxy with predefined destinations
package examples;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.config.ProtocolConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.exceptions.ProxyException;

public class TestHttp1Cluster {

    private static final Logger logger = LoggerFactory.getLogger(TestHttp1Cluster.class);
    private static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));
    private static final String LOCAL_HOST = "localhost";
    private static final List<String> routingDestinations = List.of("localhost:8001", "localhost:8002", "localhost:8003");

    public static void main(String[] args) throws ProxyException {
        ProxyServer proxy = new ProxyServer(new ProtocolConfig("HTTP1", LOCAL_PORT, true));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_HOST, routingDestinations));
            proxy.start();
            
            logger.info("Proxy server is running. Press Ctrl+C to stop.");
            
            proxy.sync();
            
        } catch (ProxyException e) {
            logger.error("Failed to start proxy server: {}", e.getMessage());
            proxy.stop();
        } finally {
            logger.info("Proxy server is shutting down");
            proxy.stop();
        }
    }
}
```

The cluster example routes requests to:
- **Original destination** (from the request URL)
- **localhost:8001** (cluster destination 1)
- **localhost:8002** (cluster destination 2)
- **localhost:8003** (cluster destination 3)

### Making Requests

#### HTTP Requests
```bash
# Simple GET request
curl -x localhost:8000 "http://echo.free.beeceptor.com/sample-request?author=beeceptor"

# POST request with JSON body
curl -x localhost:8000 \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"name": "John", "age": 30}' \
  "http://echo.free.beeceptor.com/sample-request"

# Request with compression
curl -x localhost:8000 \
  -H "Accept-Encoding: gzip" \
  "http://echo.free.beeceptor.com/sample-request"
```

#### HTTPS Requests (Tunneling)
```bash
# HTTPS request via CONNECT tunneling
curl -x localhost:8000 "https://echo.free.beeceptor.com/sample-request?author=beeceptor"
```

## Clustering
```
Client Request
      ↓
  Proxy Server
      ├── → Original Destination (echo.free.beeceptor.com)
      ├── → Cluster Destination 1 (localhost:8001)
      ├── → Cluster Destination 2 (localhost:8002)
      └── → Cluster Destination 3 (localhost:8003)
      
All responses collected and aggregated
      ↓
Combined JSON Response → Client
```

### Clustering Limitations

- **HTTPS requests**: Clustering is **not supported** for HTTPS requests.
- **WebSocket connections**: Not supported in cluster mode
- **Streaming responses**: Large responses are fully buffered before aggregation