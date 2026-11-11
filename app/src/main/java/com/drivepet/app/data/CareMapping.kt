package com.drivepet.app.data

import com.drivepet.app.data.db.MaintenanceItemEntity
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.domain.careItemsFor
import java.time.LocalDate
import java.util.UUID

/** itemId는 프로필+항목에서 정한다: 온보딩을 다시 눌러도, 기본 항목을 두 번 만들어도 같은 행이 된다. */
fun careItemId(profileId: String, item: CareItem): String = UUID.nameUUIDFromBytes("care:$profileId:${item.name}".toByteArray()).toString()

fun careItemEntity(profileId: String, item: CareItem, b: CareBaseline) = MaintenanceItemEntity(
    itemId = careItemId(profileId, item), profileId = profileId, kind = item.name, intervalKm = item.intervalKm,
    baselineOdometerMm = b.odometerMm, baselineServicedOn = b.servicedOn?.toString(), baselineServicedPeriod = b.period?.name,
)

/** 기준이 모두 비어 있는(미등록) 기본 항목. */
fun defaultCareItems(profileId: String, fuel: FuelType): List<MaintenanceItemEntity> =
    careItemsFor(fuel).map { careItemEntity(profileId, it, CareBaseline(null, null, null)) }

fun MaintenanceItemEntity.careItem(): CareItem? = runCatching { CareItem.valueOf(kind) }.getOrNull()

fun MaintenanceItemEntity.baseline() = CareBaseline(
    baselineOdometerMm,
    baselineServicedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    baselineServicedPeriod?.let { runCatching { ServicedPeriod.valueOf(it) }.getOrNull() },
)
