"use strict";

function redirect(presignedUri) {
  return {
    statusCode: 302,
    headers: { Location: presignedUri },
    body: ''
  };
}

function bytestream(buffer, contentType) {
  return {
    statusCode: 200,
    headers: { 'Content-Type': contentType || 'application/pdf' },
    body: buffer.toString('base64'),
    isBase64Encoded: true
  };
}

function error(statusCode, title, detail) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/problem+json' },
    body: JSON.stringify({ status: statusCode, title, detail })
  };
}

module.exports = { redirect, bytestream, error };
