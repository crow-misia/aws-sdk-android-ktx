package com.example.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sample.R

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MainContent(viewModel)
    }
}

@Composable
fun MainContent(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::onClickProvisioning) {
            Text(text = stringResource(R.string.provisioning))
        }
        Button(onClick = viewModel::onClickSubscribeShadow) {
            Text(text = stringResource(R.string.subscribe_shadow))
        }
        Button(onClick = viewModel::onClickSend) {
            Text(text = stringResource(R.string.send_message))
        }
        Button(onClick = viewModel::onClickGetShadow) {
            Text(text = stringResource(R.string.get_shadow))
        }
        Button(onClick = viewModel::onClickUpdateShadow) {
            Text(text = stringResource(R.string.update_shadow))
        }
        Button(onClick = viewModel::onClickDeleteShadow) {
            Text(text = stringResource(R.string.delete_shadow))
        }
    }
}
