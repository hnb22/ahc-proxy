#!/bin/bash

# Initialize success counter
success_count=0
total_tests=3

echo "Starting proxy functionality tests..."
echo "================================="

# GET operation (no auth, compression)
echo "Test 1: GET operation (no auth, no compression)"
if curl -x localhost:8000 "http://echo.free.beeceptor.com/sample-request?author=beeceptor"; then
    echo "✅ GET test passed"
    ((success_count++))
else
    echo "❌ GET test failed"
fi
echo

# POST operation (body, header)
echo "Test 2: POST operation (with body and headers)"
if curl -x localhost:8000 -X POST -H "Content-Type: application/json" -d '{"name": "John Doe", "age": 30, "city": "New York"}' "http://echo.free.beeceptor.com/sample-request?author=beeceptor"; then
    echo "✅ POST test passed"
    ((success_count++))
else
    echo "❌ POST test failed"
fi
echo

# POST operation (body, header, w/compression)
echo "Test 3: POST operation (with compression)"
if curl -x localhost:8000 -X POST -H "Content-Type: application/json" -d '{"name": "John Doe", "age": 30, "city": "New York"}' "http://echo.free.beeceptor.com/sample-request?author=beeceptor" -H "Accept-Encoding: gzip"; then
    echo "✅ POST with compression test passed"
    ((success_count++))
else
    echo "❌ POST compression test failed"
fi
echo

# Final results
echo "================================="
echo "Test Results: $success_count/$total_tests tests passed"

if [ $success_count -eq $total_tests ]; then
    echo "🎉 SUCCESS: All proxy functionality tests completed successfully!"
else
    echo "⚠️  Some tests failed. Please check the proxy server and try again."
    exit 1
fi
