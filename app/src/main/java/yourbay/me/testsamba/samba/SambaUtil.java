package yourbay.me.testsamba.samba;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

/**
 * Created by ram on 15/1/16.
 */
public class SambaUtil {
    public final static String TAG = "SambaUtil";
    public final static int IO_BUFFER_SIZE = 16 * 1024;
    private final static boolean DEBUG = true;

    public final static String getRemotePath(Config config, String path) {
        return new StringBuilder("smb://").append(config.host).append(path).toString();//.append(":445")
    }

    public final static List<SmbFile> listFiles(Config config, String remotePath) throws Exception {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, config.user, config.password);
        final String mURL = remotePath;
//        try {
        if (DEBUG) {
            Log.d(TAG, "listFiles   URL=" + mURL + " " + config);
        }
        SmbFile file = new SmbFile(mURL, auth);
        return new ArrayList<SmbFile>(Arrays.asList(file.listFiles()));
//        } catch (SmbException smb) {
//            smb.printStackTrace();
//        } catch (MalformedURLException murlE) {
//            murlE.printStackTrace();
//        } catch (Exception ioE) {
//            ioE.printStackTrace();
//        }
    }

    public final static boolean upload(Config config, String localPath, String remoteFolder) throws Exception {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, config.user, config.password);
        File localFile = new File(localPath);
        String mURL = new StringBuilder("smb://").append(config.host).append(remoteFolder).append(localFile.getName()).toString();
//        try {
        if (DEBUG) {
            Log.d(TAG, "config=" + config + "  upload      URL=" + mURL);
        }
        SmbFile remoteFile = new SmbFile(mURL, auth);
        InputStream inS = new FileInputStream(localFile);
        SmbFileOutputStream outS = new SmbFileOutputStream(remoteFile);
        try {
            writeStream(inS, outS, localFile.length());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            inS.close();
            outS.flush();
            outS.close();
        }
//        } catch (SmbException smb) {
//            smb.printStackTrace();
//        } catch (MalformedURLException murlE) {
//            murlE.printStackTrace();
//        } catch (IOException ioE) {
//            ioE.printStackTrace();
//        }
        return false;
    }

    public final static boolean download(Config config, String localPath, String mURL) throws Exception {
        return download(config, localPath, mURL, -1);
    }

    public final static boolean download(Config config, String localPath, String mURL, long size) throws Exception {
        if (DEBUG) {
            Log.d(TAG, "config=" + config + "download      URL=" + mURL + "   " + localPath);
        }
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, config.user, config.password);
        File localFile = new File(localPath);
//        try {
        SmbFile remoteFile = new SmbFile(mURL, auth);
        OutputStream outS = new FileOutputStream(localFile);
        SmbFileInputStream inS = new SmbFileInputStream(remoteFile);
        try {
            writeStream(inS, outS, size);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            inS.close();
            outS.flush();
            outS.close();
        }
//        } catch (SmbException smb) {
//            smb.printStackTrace();
//        } catch (MalformedURLException murlE) {
//            murlE.printStackTrace();
//        } catch (IOException ioE) {
//            ioE.printStackTrace();
//        }
        return false;
    }


    private final static void writeStream(InputStream ins, OutputStream outs, final long totalSize) throws IOException {
        byte[] tmp = new byte[IO_BUFFER_SIZE];
        int length;
        float uploaded = 0f;
        final long startMills = System.currentTimeMillis();
        long lastMills = startMills;
        while ((length = ins.read(tmp)) != -1) {
            outs.write(tmp, 0, length);
            uploaded += length;

            //DELTA TIME
            final long currentMill = System.currentTimeMillis();
            final long delta = currentMill - lastMills;
            lastMills = currentMill;

            //SPEED
            final float speed = ((float) length) / delta;
            final float avegSpeed = uploaded / (currentMill - startMills);

            //PROGRESS
            final float progress = (totalSize <= 0) ? -1 : (uploaded * 100) / totalSize;
            if (DEBUG) {
                Log.d(TAG, "writeStream progress:" + progress + "    speed=" + speed + "/" + avegSpeed);
            }

            //TODO add progress while uploading
            //TODO upload speed!!
        }
    }

    public final static boolean createFolder(Config config, String parent, String name) throws Exception {
//        try {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, config.user, config.password);
        String mURL = wrapPath(parent, name);
        if (DEBUG) {
            Log.d(TAG, "config=" + config + "createFolder      URL=" + mURL + " parent=" + parent + " name=" + name);
        }
        SmbFile remoteFile = new SmbFile(mURL, auth);
        if (!remoteFile.exists()) {
            remoteFile.mkdir();
            return true;
        }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return false;
    }


    public final static boolean delete(Config config, String path) throws Exception {
//        try {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, config.user, config.password);
        if (DEBUG) {
            Log.d(TAG, "config=" + config + "delete      URL=" + path);
        }
        SmbFile remoteFile = new SmbFile(path, auth);
        if (remoteFile.exists()) {
            remoteFile.delete();
            return true;
        }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return false;
    }


    public final static SmbFile[] listWorkGroup() {
        try {
            if (DEBUG) {
                Log.d(TAG, "listWorkGroup");
            }
            SmbFile f = new SmbFile("smb://WORKGROUP/");
            return f.listFiles();
        } catch (MalformedURLException murlE) {
            murlE.printStackTrace();
        } catch (IOException ioE) {
            ioE.printStackTrace();
        }
        return null;
    }

    public final static String[] listWorkGroupPath() {
        try {
            if (DEBUG) {
                Log.d(TAG, "listWorkGroupPath");
            }
            SmbFile f = new SmbFile("smb://WORKGROUP/");
            SmbFile[] files = f.listFiles();
            List<String> l = new ArrayList<>();
            for (SmbFile sf : files) {
                l.add(sf.getName());
            }
            return l.toArray(new String[l.size()]);
        } catch (MalformedURLException murlE) {
            murlE.printStackTrace();
        } catch (IOException ioE) {
            ioE.printStackTrace();
        }
        return null;
    }

    public final static String getFileName(String path) {
        if (path == null) {
            return path;
        }
        if (!path.contains("/")) {
            return path;
        }
        int index = path.lastIndexOf("/");
        if (index < 0 || index + 1 >= path.length()) {
            return path;
        }
        return path.substring(index + 1);
    }

    public final static String strsToString(SmbFile[] strs) {
        if (strs == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(strs.length);
        for (SmbFile smbFile : strs) {
            builder.append("\n");
            builder.append("[");
            builder.append(smbFile.getPath());
            builder.append("]");
        }
        return builder.toString();
    }

    public final static String strsToString(String[] strs) {
        if (strs == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String str : strs) {
            builder.append("\n");
            builder.append("[");
            builder.append(str);
            builder.append("]");
        }
        return builder.toString();
    }

    public final static String wrapPath(String parent, String name) {
        if (TextUtils.isEmpty(parent) || TextUtils.isEmpty(name)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(parent);
        if (!parent.endsWith("/")) {
            builder.append("/");
        }
        if (name.endsWith("/")) {
            int index = name.lastIndexOf("/");
            name = name.substring(0, index - 1);
        }
        builder.append(name);
        return builder.toString();
    }

}
