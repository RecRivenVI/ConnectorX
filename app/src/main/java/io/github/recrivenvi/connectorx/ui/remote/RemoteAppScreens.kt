package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.runtime.Composable
import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.session.RemoteSession

@Composable
internal fun RemoteApplicationPage(engine: ConnectorEngine, session: RemoteSession) {
    NetworkSessionPage(engine, session)
}
