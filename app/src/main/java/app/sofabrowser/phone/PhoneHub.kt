package app.sofabrowser.phone

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The one phone server and pairing token for the whole app, shared by the screens that take phone commands (the
 * browser and Multiview). Commands go to the screen most recently started that is still started, so the phone keeps
 * the same address and pairing when the TV switches between them: Android starts the new screen before it stops the
 * old one, and the server never stops in between. It stops when no screen is started, as before.
 *
 * Main thread only, except the [PhoneServer.Actions] calls, which come from server threads.
 */
object PhoneHub : PhoneServer.Actions {

    private val targets = CopyOnWriteArrayList<PhoneServer.Actions>()
    private var tokenStore: TokenStore? = null
    private var server: PhoneServer? = null

    fun tokens(context: Context): TokenStore = synchronized(this) {
        tokenStore ?: TokenStore(context.applicationContext).also { tokenStore = it }
    }

    private fun server(context: Context): PhoneServer = synchronized(this) {
        server ?: PhoneServer(context.applicationContext, tokens(context), this).also { server = it }
    }

    /** A screen was started: it takes the phone's commands, and the server runs if a phone was ever paired. */
    fun attach(context: Context, target: PhoneServer.Actions) {
        targets.remove(target)
        targets.add(target)
        if (tokens(context).enabled) server(context).start()
    }

    /** A screen was stopped; with none left started, the server stops. */
    fun detach(target: PhoneServer.Actions) {
        targets.remove(target)
        if (targets.isEmpty()) server?.stop()
    }

    /** The QR code was shown: the phone page is on from now on, while a screen is started. */
    fun enable(context: Context) {
        tokens(context).enabled = true
        if (targets.isNotEmpty()) server(context).start()
    }

    fun port(context: Context): Int = server(context).port

    /** A new token, so old QR codes and Shortcuts stop working, and the phone page off until paired again. */
    fun forget(context: Context) {
        server?.stop()
        tokens(context).rotate()
        tokens(context).enabled = false
    }

    private fun top(): PhoneServer.Actions? = targets.lastOrNull()

    override fun openUrl(input: String) = top()?.openUrl(input) ?: false
    override fun type(text: String) { top()?.type(text) }
    override fun key(name: String) = top()?.key(name) ?: false
    override fun pointer(cmd: PointerProtocol.Command) { top()?.pointer(cmd) }
    override fun pointerConnected() { top()?.pointerConnected() }
    override fun pointerDisconnected() { targets.forEach { it.pointerDisconnected() } }
    override fun feeds(): List<String?>? = top()?.feeds()
    override fun setFeed(slot: Int, input: String) = top()?.setFeed(slot, input) ?: PhoneServer.FeedReply.NOT_MULTIVIEW
}
