package com.bit128.bitsports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.account.Account128
import com.bit128.bitsports.calendar.BitcalSync
import kotlinx.coroutines.launch

/** Sign in once with the 128bit account and every game of your NHL teams lands in Bitcal. */
@Composable
internal fun BitcalSettings() {
    val ctx = LocalContext.current
    val account = remember { Account128(ctx) }
    val scope = rememberCoroutineScope()
    // Bumped after anything changes so the status lines re-read what's saved.
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val accent = Color(LocalUi.current.accent)

    CardBox {
        Column(Modifier.padding(16.dp)) {
            key(tick) {
                if (!account.signedIn) {
                    var email by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf<String?>(null) }
                    Text("Put every game of your teams in Bitcal: home and away, preseason left out, playoffs added as they're set. Times, TV and final scores keep up on their own.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Field(email, { email = it }, "Email", KeyboardType.Email, secret = false)
                    Spacer(Modifier.height(8.dp))
                    Field(password, { password = it }, "Password", KeyboardType.Password, secret = true)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    account.signIn(email, password)
                                    BitcalSync.setEnabled(ctx, true)
                                    BitcalSync.sync(ctx, force = true) { tick++ ; busy = false }
                                } catch (e: Exception) {
                                    error = e.message ?: "Couldn't sign in"
                                    busy = false
                                }
                                tick++
                            }
                        },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) { Text(if (busy) "Signing in…" else "Sign in", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "The same 128bit account as Bitcal and 128bit Play.", color = if (error != null) Live else Dim, fontSize = 12.sp)
                } else {
                    val on = BitcalSync.enabled(ctx)
                    Text("Signed in as ${account.email ?: "your 128bit account"}", color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(status(ctx, busy), color = BitcalSync.lastError(ctx)?.let { Live } ?: Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Keep my games in Bitcal", on) { BitcalSync.setEnabled(ctx, it); if (it) { busy = true; BitcalSync.sync(ctx, force = true) { busy = false; tick++ } }; tick++ }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Outline("Update now", enabled = on && !busy, Modifier.weight(1f)) {
                            busy = true; BitcalSync.sync(ctx, force = true) { busy = false; tick++ }
                        }
                        Outline("Sign out", enabled = !busy, Modifier.weight(1f)) { account.signOut(); tick++ }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { busy = true; BitcalSync.removeAll(ctx) { busy = false; tick++ } }, enabled = !busy) {
                        Text("Remove the games from Bitcal", color = Dim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun status(ctx: android.content.Context, busy: Boolean): String {
    if (busy) return "Updating Bitcal…"
    BitcalSync.lastError(ctx)?.let { return "Last update failed: $it" }
    val last = BitcalSync.lastSync(ctx) ?: return "Not updated yet"
    val mins = (System.currentTimeMillis() - last) / 60_000
    val ago = when { mins < 1 -> "just now"; mins < 60 -> "${mins}m ago"; mins < 48 * 60 -> "${mins / 60}h ago"; else -> "${mins / 1440}d ago" }
    return "${BitcalSync.count(ctx)} games in the \"bitsports · NHL\" calendar · updated $ago"
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType, secret: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(label, color = Dim) },
        singleLine = true, shape = RoundedCornerShape(11.dp),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Card2, unfocusedContainerColor = Card2,
            focusedBorderColor = Line2, unfocusedBorderColor = Line,
            focusedTextColor = TextInk, unfocusedTextColor = TextInk,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Outline(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
        modifier = modifier.height(44.dp),
    ) { Text(label, color = if (enabled) TextInk else Dim, fontWeight = FontWeight.SemiBold) }
}
