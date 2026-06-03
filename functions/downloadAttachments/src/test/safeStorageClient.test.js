"use strict";

const { expect } = require('chai');
const proxyquire = require('proxyquire').noCallThru();

process.env.PN_SAFESTORAGE_PROTOCOL = 'http';
process.env.PN_SAFESTORAGE_HOSTNAME = 'safestorage.test';
process.env.PN_SAFESTORAGE_PORT = '8082';
process.env.PN_SAFESTORAGE_GET_FILE_PATH = '/safestorage/internal/v1/files';
process.env.PN_SAFESTORAGE_CX_ID = 'pn-io-connector';
process.env.PN_SAFESTORAGE_PRESIGNED_URL = 'download.url';

const FILE_KEY = 'safestorage-key-xyz';
const PRESIGNED_URI = 'https://s3.example.com/presigned?token=xyz';

function makeHttpMock(statusCode, responseBody) {
  return {
    request: (_options, callback) => {
      const body = typeof responseBody === 'string' ? responseBody : JSON.stringify(responseBody);
      const res = {
        statusCode,
        headers: { 'content-type': 'application/json' },
        on: (event, handler) => {
          if (event === 'data') handler(body);
          if (event === 'end') handler();
          return res;
        }
      };
      callback(res);
      return { on: () => {}, end: () => {} };
    }
  };
}

function makeClient(mockHttp) {
  return proxyquire('../app/lib/safeStorageClient', { http: mockHttp });
}

describe('safeStorageClient', () => {
  it('should return the presigned URI from the download.url field', async () => {
    const client = makeClient(makeHttpMock(200, { download: { url: PRESIGNED_URI } }));
    const result = await client.getPresignedUri(FILE_KEY);
    expect(result).to.equal(PRESIGNED_URI);
  });

  it('should throw when SafeStorage returns a non-200 status', async () => {
    const client = makeClient(makeHttpMock(500, { status: 500, detail: 'Internal Error' }));
    try {
      await client.getPresignedUri(FILE_KEY);
      expect.fail('Should have thrown');
    } catch (err) {
      expect(err.message).to.include('500');
    }
  });

  it('should throw when the URL field is missing from the response', async () => {
    const client = makeClient(makeHttpMock(200, { other: 'field' }));
    try {
      await client.getPresignedUri(FILE_KEY);
      expect.fail('Should have thrown');
    } catch (err) {
      expect(err.message).to.include('download.url');
    }
  });

  it('should throw when the response body is not valid JSON', async () => {
    const client = makeClient(makeHttpMock(200, 'not-valid-json'));
    try {
      await client.getPresignedUri(FILE_KEY);
      expect.fail('Should have thrown');
    } catch (err) {
      expect(err).to.exist;
    }
  });

  it('should pass cx-id and Content-Type headers in the request', async () => {
    let capturedOptions;
    const httpMock = {
      request: (options, callback) => {
        capturedOptions = options;
        const res = {
          statusCode: 200,
          headers: {},
          on: (event, handler) => {
            if (event === 'data') handler(JSON.stringify({ download: { url: PRESIGNED_URI } }));
            if (event === 'end') handler();
            return res;
          }
        };
        callback(res);
        return { on: () => {}, end: () => {} };
      }
    };
    const client = makeClient(httpMock);
    await client.getPresignedUri(FILE_KEY);
    expect(capturedOptions.headers['x-pagopa-safestorage-cx-id']).to.equal('pn-io-connector');
    expect(capturedOptions.path).to.include(FILE_KEY);
  });
});
