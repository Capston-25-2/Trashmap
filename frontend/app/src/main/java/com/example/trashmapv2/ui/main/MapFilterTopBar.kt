package com.example.trashmapv2.ui.main

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.trashmapv2.R

@Composable
fun MapFilterTopBar(modifier: Modifier = Modifier) {

    val filterItems = listOf(
        "일반" to R.drawable.ic_general,
        "재활용" to R.drawable.ic_recycle,
        "음료" to R.drawable.ic_drink,
        "미션" to R.drawable.ic_mission
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filterItems) { (filterName, iconResId) ->
            AssistChip(
                onClick = { Log.d("FILTER_CLICK", "$filterName 클릭됨") },
                label = { Text(filterName) },

                leadingIcon = {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = filterName,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        tint = Color.Unspecified
                    )
                },

                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = AssistChipDefaults.assistChipBorder(
                    borderWidth = 1.dp,
                    borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )
        }
    }
}