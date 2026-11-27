package com.wkcw.scanify.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wkcw.scanify.R
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.preference.AppInfoPreference
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val (version, build) = remember(context) { appVersion(context) }
    val versionLine = stringResource(R.string.about_version_format, version, build)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CouiPage(
        title = stringResource(R.string.about_title),
        modifier = modifier,
        onBack = onBack,
        wide = isWideWindow(),
        snackbarHostState = snackbarHostState,
    ) {
        item(key = "app_header") {
            AppInfoPreference(
                appName = stringResource(R.string.app_name),
                appVersion = versionLine,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_app),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                copyText = stringResource(R.string.about_copy_version),
                onCopyClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("version", versionLine))
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.about_copied_snackbar))
                    }
                },
            )
        }

        item(key = "app_details") {
            PreferenceCategory(title = stringResource(R.string.about_category_info))
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                CardListGroup {
                    item(key = "version") {
                        BasicComponent(
                            title = stringResource(R.string.about_version_label),
                            endActions = {
                                Text(
                                    text = version,
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelTertiary,
                                )
                            },
                        )
                    }
                    item(key = "build") {
                        BasicComponent(
                            title = stringResource(R.string.about_build_label),
                            endActions = {
                                Text(
                                    text = build,
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelTertiary,
                                )
                            },
                        )
                    }
                    item(key = "description") {
                        BasicComponent(
                            title = stringResource(R.string.about_description_title),
                            summary = stringResource(R.string.about_description_summary),
                        )
                    }
                }
            }
        }

        item(key = "project_links") {
            PreferenceCategory(title = stringResource(R.string.about_category_links))
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                CardListGroup {
                    item(key = "source_code") {
                        ArrowPreference(
                            title = stringResource(R.string.about_source_title),
                            summary = stringResource(R.string.about_source_summary),
                            startAction = {
                                Icon(
                                    imageVector = COUIIcons.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = COUITheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/WKCW-Project/Scanify"),
                                )
                                context.startActivity(intent)
                            },
                        )
                    }
                }
            }
        }

        item(key = "copyright") {
            Text(
                text = stringResource(R.string.about_copyright),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = COUITheme.textStyles.bodyXS,
                color = COUITheme.colorScheme.labelTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun appVersion(context: Context): Pair<String, String> {
    val packageManager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(context.packageName, 0)
    }
    val unknown = context.getString(R.string.about_unknown)
    return (info.versionName ?: unknown) to info.longVersionCode.toString()
}
