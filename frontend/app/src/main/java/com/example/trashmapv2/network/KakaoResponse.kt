package com.example.trashmapv2.network

import com.google.gson.annotations.SerializedName

// 1. 전체 응답 껍데기
data class KakaoCoord2AddressResponse(
    @SerializedName("documents") val documents: List<AddressDocument>
)

// 2. 문서 (주소 정보 하나)
data class AddressDocument(
    @SerializedName("road_address") val roadAddress: RoadAddress?, // 도로명 주소 (없을 수도 있음)
    @SerializedName("address") val address: Address?               // 지번 주소
)

// 3. 도로명 주소 상세
data class RoadAddress(
    @SerializedName("address_name") val addressName: String
)

// 4. 지번 주소 상세
data class Address(
    @SerializedName("address_name") val addressName: String

)