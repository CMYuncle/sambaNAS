package yourbay.me.testsamba.samba.httpd;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import yourbay.me.testsamba.samba.SambaHelper;
import yourbay.me.testsamba.samba.SambaUtil;

/**
 * Created by ram on 15/1/13.
 */
public class NanoSmbStreamer extends NanoHTTPD implements IStreamer {
    public final static String TAG = SambaHelper.TAG;
    public final static int DEFAULT_SERVER_PORT = 8000;
    private int serverPort;

    public NanoSmbStreamer() {
        this(DEFAULT_SERVER_PORT);
    }

    public NanoSmbStreamer(int port) {
        super(null, port);
        this.serverPort = port;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        String uri = session.getUri();
        return respond(headers, uri);
    }

    private Response respond(Map<String, String> headers, String uri) {
        String smbUri = SambaUtil.cropStreamURL(uri);
        String mimeTypeForFile = SambaUtil.getVideoMimeType(uri);
        if (SambaHelper.DEBUG) {
            Log.d(SambaHelper.TAG, "respond headers=" + (Arrays.toString(headers.values().toArray())) + "    smbUri=" + smbUri + "  MIME=" + mimeTypeForFile);
        }
        Response response = null;
        try {
            SmbFile smbFile = new SmbFile(smbUri);
            InputStream copyStream = new BufferedInputStream(new SmbFileInputStream(smbFile));
            response = serveSmbFile(smbUri, headers, copyStream, smbFile, mimeTypeForFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (SambaHelper.DEBUG) {
            Log.d(SambaHelper.TAG, "respond response=" + (response != null));
        }
        return response != null ? response : createResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Error 404, file not found.");
    }

    // Announce that the file server accepts partial content requests
    private Response createResponse(Response.Status status, String mimeType, String message) {
        Response res = new Response(status, mimeType, message);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    private Response createNonBufferedResponse(Response.Status status, String mimeType, InputStream message, Long len) {
        Response res = new NonBufferedResponse(status, mimeType, message, len);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    @Override
    public void start() {
        try {
            super.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopStream() {
        closeAllConnections();
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public String getIp() {
        return SambaUtil.getLocalIpAddress();
    }

    private Response serveSmbFile(String smbFileUrl, Map<String, String> header, InputStream is, SmbFile smbFile, String mime) {
        Response res;
        try {
            // Calculate etag
            String etag = Integer.toHexString((smbFile.getName() + smbFile.getLastModified() + "" + smbFile.length()).hashCode());

            // Support (simple) skipping:
            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range.substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Change return code and add Content-Range header when skipping is requested
            long fileLen = smbFile.length();
            Log.d(TAG, "fileLen fileLen=" + fileLen + "/available=" + is.available() + " header=" + Arrays.toString(header.entrySet().toArray()));
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    res = createResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
                    res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                    res.addHeader("ETag", etag);
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1;
                    }
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0) {
                        newLen = 0;
                    }

                    final long dataLen = newLen;//
                    is.skip(startFrom);

                    res = createNonBufferedResponse(Response.Status.PARTIAL_CONTENT, mime, is, fileLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                    res.addHeader("ETag", etag);
                }
            } else {
                if (etag.equals(header.get("if-none-match")))
                    res = createResponse(Response.Status.NOT_MODIFIED, mime, "");
                else {
                    res = createNonBufferedResponse(Response.Status.OK, mime, is, fileLen);
                    res.addHeader("ETag", etag);
                }
            }
        } catch (IOException ioe) {
            res = createResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
        }

        return res;
    }

    public static class NonBufferedResponse extends Response {
        private long available;

        /**
         * Basic constructor.
         */
        public NonBufferedResponse(Status status, String mimeType, InputStream data, long available) {
            super(status, mimeType, data);
            this.available = available;
        }


        @Override
        protected void sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, Map<String, String> header, int size) {
//            super.sendContentLengthHeaderIfNotAlreadyPresent(pw, header, size);
            long pending = (getData() != null ? available : 0); // This is to support partial sends, see serveFile()
            String string = header.get("Content-Range"); // Such as bytes 203437551-205074073/205074074
            if (string != null) {
                if (string.startsWith("bytes ")) {
                    string = string.substring("bytes ".length());
                }
                Long start = Long.parseLong(string.split("-")[0]);
                pw.print("Content-Length: " + (pending - start) + "\r\n");
            } else {
                pw.print("Content-Length: " + pending + "\r\n");
            }
        }

        @Override
        protected void sendAsFixedLength(OutputStream outputStream, int pending) throws IOException {
//            super.sendAsFixedLength(outputStream, pending);
            sendAsFixedLength(outputStream);
        }

        private void sendAsFixedLength(OutputStream outputStream) throws IOException {
            long pending = (getData() != null ? available : 0);
            if (getRequestMethod() != Method.HEAD && getData() != null) {
                int BUFFER_SIZE = 16 * 1024;
                byte[] buff = new byte[BUFFER_SIZE];
                while (pending > 0) {
                    // Note the ugly cast to int to support > 2gb files. If pending < BUFFER_SIZE we can safely cast anyway.
                    int read = getData().read(buff, 0, ((pending > BUFFER_SIZE) ? BUFFER_SIZE : (int) pending));
                    if (read <= 0) {
                        break;
                    }
                    outputStream.write(buff, 0, read);
                    pending -= read;
                }
            }
        }


//        protected void sendAsFixedLength(OutputStream outputStream, PrintWriter pw) throws IOException {
//            // Need to set Content-Length as range END - START
//            long pending = (long) (getData() != null ? available : 0); // This is to support partial sends, see serveFile()
//            String string = getHeader("Content-Range"); // Such as bytes 203437551-205074073/205074074
////            String string = header.get("Content-Range");
//            Log.d(TAG, "sendAsFixedLength   Content-Range=" + String.valueOf(string));
//            if (string != null) {
//                if (string.startsWith("bytes ")) {
//                    string = string.substring("bytes ".length());
//                }
//                Long start = Long.parseLong(string.split("-")[0]);
//                pw.print("Content-Length: " + (pending - start) + "\r\n");
//            } else {
//                pw.print("Content-Length: " + pending + "\r\n");
//            }
//
//            pw.print("\r\n");
//            pw.flush();
//
//            if (getRequestMethod() != Method.HEAD && getData() != null) {
//                int BUFFER_SIZE = 16 * 1024;
//                byte[] buff = new byte[BUFFER_SIZE];
//                while (pending > 0) {
//                    // Note the ugly cast to int to support > 2gb files. If pending < BUFFER_SIZE we can safely cast anyway.
//                    int read = getData().read(buff, 0, ((pending > BUFFER_SIZE) ? BUFFER_SIZE : (int) pending));
//                    if (read <= 0) {
//                        break;
//                    }
//                    outputStream.write(buff, 0, read);
//
//                    pending -= read;
//                }
//            }
//        }

    }


}
