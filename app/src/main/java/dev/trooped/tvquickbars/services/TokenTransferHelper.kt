package dev.trooped.tvquickbars.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder

/**
 * TokenTransferHelper Class
 * Manages the local URL+token transfer server + QR code generation.
 */
class TokenTransferHelper(private val context: Context) {

    private var serverUrl = "http://localhost:8765"
    private val TAG = "TokenTransferHelper"
    private var tokenServer: TokenReceiverServer? = null

    /**
     * Gets the local IP address of the device.
     * @return The local IP address as a string, or null if not found.
     */
    fun getLocalIpAddress(): String? {
        try {
            // First try WiFi
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                    return Formatter.formatIpAddress(wifiInfo.ipAddress)
                }
            }

            // If WiFi doesn't work, try all network interfaces (including Ethernet)
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IP_ADDRESS", "Error getting IP address", e)
        }
        return null
    }

    /**
     * Generates a QR code bitmap.
     * @param content The content to encode in the QR code.
     * @return A bitmap containing the QR code.
     */
    fun generateQRCode(content: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return bmp
    }

    /**
     * Starts the token server.
     * @param tokenOnlyMode If true, only the token field will be shown (for Settings screen)
     * @param onTokenReceived Callback for when credentials are received.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun startTokenTransfer(
        tokenOnlyMode: Boolean = false,
        onTokenReceived: (String, String) -> Unit
    ) {
        try {
            // Try to stop any existing server first
            stopServer()

            // Small delay to ensure port is released
            Thread.sleep(100)

            // Try to start the server with the default port
            startServerWithPort(8765, tokenOnlyMode, onTokenReceived)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server on default port: ${e.message}")

            try {
                // If default port fails, try alternative ports
                for (port in listOf(8766, 8767, 8768, 8769)) {
                    try {
                        Log.d(TAG, "Trying alternative port: $port")
                        startServerWithPort(port, tokenOnlyMode, onTokenReceived)
                        // If we reach here, server started successfully
                        return
                    } catch (e2: Exception) {
                        // Keep trying other ports
                        Log.e(TAG, "Failed to start on port $port: ${e2.message}")
                    }
                }

                // If all ports failed, throw exception
                throw Exception("All server ports are in use")
            } catch (e2: Exception) {
                Log.e(TAG, "Critical server error: ${e2.message}")
                throw e2
            }
        }
    }

    private fun startServerWithPort(
        port: Int,
        tokenOnlyMode: Boolean = false,
        onTokenReceived: (String, String) -> Unit
    ) {
        // Update the server URL with the selected port
        val ipAddress = getLocalIpAddress()
        if (ipAddress != null) {
            serverUrl = "http://$ipAddress:$port"
        }

        // Create and start the token server with the specified port
        tokenServer = TokenReceiverServer(port, tokenOnlyMode) { haUrl, token ->
            // Process token but don't stop server yet
            context.mainExecutor.execute {
                onTokenReceived(haUrl, token)

                // Delay server shutdown to allow the response to be sent
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    stopServer()
                }, 500) // 500ms delay to ensure response is sent
            }
        }
        tokenServer?.start()
        Log.d(TAG, "Server started on port $port with URL $serverUrl")
    }

    /**
     * Stops the token server.
     */
    fun stopServer() {
        try {
            tokenServer?.stop()
            tokenServer = null
            Log.d(TAG, "Server stopped")

            // Allow a moment for the port to be released
            Thread.sleep(100)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server: ${e.message}")
        }
    }

    /**
     * NanoHTTPD server for receiving tokens and URLs.
     */
    class TokenReceiverServer(
        port: Int,
        private val tokenOnlyMode: Boolean = false,
        private val onTokenReceived: (String, String) -> Unit
    ) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            return if (session.method == Method.POST) {
                session.parseBody(mutableMapOf())

                var haUrl = session.parms["url"] ?: ""
                var token = session.parms["token"] ?: ""

                // Decode and trim
                haUrl = URLDecoder.decode(haUrl, "UTF-8").trim()
                token = URLDecoder.decode(token, "UTF-8")
                    .replace("\r", "")
                    .replace("\n", "")
                    .trim()

                if (token.isNotEmpty()) {
                    onTokenReceived(haUrl, token)

                    // Return success page
                    val successHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                        body {
                            font-family: 'Roboto', Arial, sans-serif;
                            line-height: 1.6;
                            color: #DEE3E6;
                            background-color: #0F1416;
                            margin: 0;
                            padding: 20px;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                        }
                        .container {
                            background-color: #1B2023;
                            padding: 30px;
                            border-radius: 16px;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                            text-align: center;
                            max-width: 500px;
                            width: 100%;
                            border: 1px solid #40484C;
                        }
                        h2 {
                            color: #87D1EB;
                            margin-top: 0;
                        }
                        .success-icon {
                            color: #B5EBFF;
                            font-size: 48px;
                            margin-bottom: 16px;
                        }
                        p {
                            margin: 16px 0;
                            color: #BFC8CC;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="success-icon">✓</div>
                        <h2>Token Received!</h2>
                        <p>Your Home Assistant token has been sent to your TV.</p>
                        <p>You can now close this page and return to your TV.</p>
                    </div>
                </body>
                </html>
                """
                    newFixedLengthResponse(successHtml)
                } else {
                    newFixedLengthResponse("No token provided.")
                }
            } else {
                // Choose which form to display based on mode
                val html = if (tokenOnlyMode) {
                    getTokenOnlyHtml()
                } else {
                    getFullFormHtml()
                }
                newFixedLengthResponse(html)
            }
        }

        private fun getTokenOnlyHtml(): String {
            return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body {
                        font-family: 'Roboto', Arial, sans-serif;
                        line-height: 1.6;
                        color: #DEE3E6;
                        background-color: #0F1416;
                        margin: 0;
                        padding: 20px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background-color: #1B2023;
                        padding: 30px;
                        border-radius: 16px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                        max-width: 500px;
                        width: 100%;
                        border: 1px solid #40484C;
                    }
                    h2 {
                        color: #87D1EB;
                        margin-top: 0;
                        margin-bottom: 24px;
                    }
                    .form-group {
                        margin-bottom: 24px;
                    }
                    label {
                        display: block;
                        margin-bottom: 8px;
                        font-weight: 500;
                        color: #BFC8CC;
                    }
                    input {
                        width: 100%;
                        padding: 12px 16px;
                        font-size: 16px;
                        border: 2px solid #40484C;
                        border-radius: 8px;
                        box-sizing: border-box;
                        background-color: #252B2D;
                        color: #DEE3E6;
                    }
                    input:focus {
                        border-color: #87D1EB;
                        outline: none;
                    }
                    button {
                        background-color: #004E60;
                        color: #B5EBFF;
                        border: none;
                        padding: 12px 24px;
                        font-size: 16px;
                        border-radius: 8px;
                        cursor: pointer;
                        width: 100%;
                        font-weight: 500;
                    }
                    button:hover {
                        background-color: #005E72;
                    }
                    .help-text {
                        margin-top: 24px;
                        color: #8A9296;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Update Access Token</h2>
                    <form method="POST">
                        <div class="form-group">
                            <label for="token">Long-lived access token:</label>
                            <input type="text" id="token" name="token" placeholder="Paste your token here" autofocus>
                        </div>
                        <button type="submit">Send to TV</button>
                    </form>
                    <p class="help-text">Your token will be sent directly to your TV app and will never leave your local network.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        }

        private fun getFullFormHtml(): String {
            return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body {
                        font-family: 'Roboto', Arial, sans-serif;
                        line-height: 1.6;
                        color: #DEE3E6;
                        background-color: #0F1416;
                        margin: 0;
                        padding: 20px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background-color: #1B2023;
                        padding: 30px;
                        border-radius: 16px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                        max-width: 500px;
                        width: 100%;
                        border: 1px solid #40484C;
                    }
                    h2 {
                        color: #87D1EB;
                        margin-top: 0;
                        margin-bottom: 24px;
                    }
                    .form-group {
                        margin-bottom: 24px;
                    }
                    label {
                        display: block;
                        margin-bottom: 8px;
                        font-weight: 500;
                        color: #BFC8CC;
                    }
                    input {
                        width: 100%;
                        padding: 12px 16px;
                        font-size: 16px;
                        border: 2px solid #40484C;
                        border-radius: 8px;
                        box-sizing: border-box;
                        background-color: #252B2D;
                        color: #DEE3E6;
                    }
                    input:focus {
                        border-color: #87D1EB;
                        outline: none;
                    }
                    button {
                        background-color: #004E60;
                        color: #B5EBFF;
                        border: none;
                        padding: 12px 24px;
                        font-size: 16px;
                        border-radius: 8px;
                        cursor: pointer;
                        width: 100%;
                        font-weight: 500;
                    }
                    button:hover {
                        background-color: #005E72;
                    }
                    .help-text {
                        margin-top: 24px;
                        color: #8A9296;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Connect to Home Assistant</h2>
                    <form method="POST">
                        <div class="form-group">
                            <label for="url">Home Assistant URL (e.g., 192.168.1.x, https://remote.xyz.a):</label>
                            <input type="text" id="url" name="url" placeholder="192.168.x.x">
                        </div>
                        <div class="form-group">
                            <label for="token">Long-lived access token:</label>
                            <input type="text" id="token" name="token" placeholder="long-lived-token" autofocus>
                        </div>
                        <button type="submit">Send to TV</button>
                    </form>
                    <p class="help-text">Your credentials will be sent directly to your TV app and will never leave your local network.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        }
    }
}