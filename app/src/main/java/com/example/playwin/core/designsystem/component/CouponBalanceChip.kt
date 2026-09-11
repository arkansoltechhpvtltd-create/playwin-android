package com.example.playwin.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.PlayWinTheme

@Composable
fun CouponBalanceChip(
    count: Int,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.ConfirmationNumber,
                contentDescription = null,
                modifier = Modifier.padding(0.dp)
            )
        },
        label = {
            Text(
                text = "$count coupons",
                style = MaterialTheme.typography.labelLarge
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        border = null
    )
}

@Preview
@Composable
private fun CouponBalanceChipPreview() {
    PlayWinTheme {
        CouponBalanceChip(count = 3)
    }
}
