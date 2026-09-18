package team.sakhi.android.feature.profile

/**
 * The one address Sakhi tells people to write to.
 *
 * Every screen and every piece of legal copy reads it from here. It used to be written out
 * 28 times across the two apps as `hello@getswipe.in`, which was both the wrong address and
 * a different one from the published privacy policy, and which Karan settled on 2026-09-18:
 * "hello@getswipe.in bilkul hat jayega, sakhi se swipe ka koi relevant nahi hai."
 *
 * It then briefly became contact@sakhiapp.in, which matched the website and still could not
 * receive mail: sakhiapp.in is a parked domain with no MX record. teamsakhi.com is the one
 * with a mail server, and Karan chose contact@teamsakhi.com the same day.
 *
 * The long legal bodies in `strings.xml` carry [TOKEN] rather than the address, because an
 * address cannot be referenced from inside another string resource. `ContentPageScreen`
 * swaps it in when it renders. A plain token and not `%1$s`: one FAQ body contains a literal
 * "85–95%", and formatting every body to reach twelve of them would throw on that one.
 *
 * Changing the address is this file.
 */
internal object SakhiContact {

    const val EMAIL = "contact@teamsakhi.com"

    /** What the legal bodies in `strings.xml` carry in place of the address. */
    const val TOKEN = "{email}"

    /** The address put back into a body that carries [TOKEN]. */
    fun resolve(body: String): String =
        if (body.contains(TOKEN)) body.replace(TOKEN, EMAIL) else body
}
