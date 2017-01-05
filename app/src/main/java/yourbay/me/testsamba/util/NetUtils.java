package yourbay.me.testsamba.util;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

/**
 * Created by tenda on 2017/1/5.
 */

public class NetUtils {
    public static String getGateWay(Context context){
       WifiManager wifiManager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if(dhcpInfo==null){
            return null;
        }
        return android.text.format.Formatter.formatIpAddress(dhcpInfo.gateway);
    }
}
