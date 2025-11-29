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
fun MapFilterTopBar(
    modifier: Modifier = Modifier,
    selectedIds: Set<Int>, // [추가] 현재 선택된 필터 ID 목록
    onFilterClick: (Int) -> Unit // [추가] 클릭 이벤트 핸들러
) {

    // ID, 이름, 아이콘 리소스를 함께 정의 (Triple 사용)
    // ID 기준: 1=일반, 2=재활용, 3=음료, 4=미션(임시)
    val filterItems = listOf(
        Triple(1, "일반", R.drawable.ic_general),
        Triple(2, "재활용", R.drawable.ic_recycle),
        Triple(3, "음료", R.drawable.ic_drink),
        Triple(4, "미션", R.drawable.ic_mission)
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filterItems) { (id, filterName, iconResId) ->

            // 현재 이 칩이 선택되었는지 확인
            val isSelected = selectedIds.contains(id)

            AssistChip(
                onClick = {
                    Log.d("FILTER_CLICK", "$filterName 클릭됨 (ID: $id)")
                    onFilterClick(id) // MainActivity로 클릭된 ID 전달
                },
                label = {
                    Text(
                        text = filterName,
                        // 선택되면 글자색을 진하게, 아니면 기본색
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = filterName,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        // 선택되면 아이콘 색을 원래대로(Unspecified), 안 되면 회색조(OnSurface)로 해도 됨
                        tint = Color.Unspecified
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    // [핵심] 선택되면 배경색 변경 (SecondaryContainer), 아니면 흰색(Surface)
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = AssistChipDefaults.assistChipBorder(
                    // 선택되면 테두리 없음(null), 아니면 회색 테두리
                    borderWidth = if (isSelected) 0.dp else 1.dp,
                    borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )
        }
    }
}