package com.plexobject.storage.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


open class TokenAuthenticationService(internal val secret: String, internal val algorithm: String, internal val expiresInMinutes: Long) {
    companion object {
        private val logger = LoggerFactory.getLogger(TokenAuthenticationService::class.java)
        internal val ROLE = "role"
        internal val APPLICATION_ROLE = "application"
        internal val BEARER_TOKEN = "Bearer"
        internal val BASIC_TOKEN = "Basic"
        internal val HEADER_STRING = "Authorization"
    }

    fun addAuthentication(res: HttpServletResponse, username: String) {
        val JWT = generateToken(username)
        res.addHeader(HEADER_STRING, "$BEARER_TOKEN $JWT")
    }

    @Throws(UnsupportedEncodingException::class)
    fun getAuthentication(request: HttpServletRequest): Authentication? {
        // for local UI access
        if ("0:0:0:0:0:0:0:1" == request.getRemoteHost() || "127.0.0.1" == request.getRemoteHost()) {
            return UsernamePasswordAuthenticationToken("", null, Collections.emptyList())
        }

        // for remote API access
        var token = request.getParameter(HEADER_STRING)
        if (token == null) {
            token = request.getHeader(HEADER_STRING)
        }
        logger.debug("Accessing API " + request.getRequestURI() + ", token " + token + ", remote "
                + request.getRemoteHost())
        if (token != null) {
            val isBasic = token!!.contains(BASIC_TOKEN)
            token = token!!.replace(".*\\s".toRegex(), "")
            if (isBasic) {
                val asBytes = Base64.getDecoder().decode(token)
                token = String(asBytes, Charset.forName("UTF-8"))
                token = token!!.substring(0, token!!.indexOf(':'))
            }
            try {
                // parse the token.
                val user = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).body.subject
                return if (user != null)
                    UsernamePasswordAuthenticationToken(user, null, Collections.emptyList())
                else
                    null
            } catch (e: RuntimeException) {
                logger.warn("Invalid token passed $token: $e")
            }

        }

        logger.debug("Failed to authenticate API " + request.getRequestURI())
        return null
    }


    fun generateToken(appName: String): String {
        val claims = Jwts.claims().setSubject(appName)
        claims[ROLE] = TokenAuthenticationService.APPLICATION_ROLE

        return Jwts.builder().setSubject(appName)
                .setExpiration(Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiresInMinutes)))
                .setClaims(claims).signWith(SignatureAlgorithm.valueOf(algorithm), secret).compact()
    }
}