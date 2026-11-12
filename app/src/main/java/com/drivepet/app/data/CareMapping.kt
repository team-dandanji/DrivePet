package com.drivepet.app.data

import com.drivepet.app.data.db.MaintenanceItemEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.CareLife
import com.drivepet.app.domain.Replacement
import com.drivepet.app.domain.careLife
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.domain.careItemsFor
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToLong
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

private val lenient = Json { ignoreUnknownKeys = true }

/** REPLACEMENT 기록 → 규칙 입력. 다른 종류거나 읽을 수 없으면 null. */
fun RecordEntity.replacement(): Pair<String, Replacement>? {
    if (kind != "REPLACEMENT" || itemId == null) return null
    val mm = runCatching { lenient.decodeFromString<ReplacementPayload>(payloadJson).odometerMm }.getOrNull()
    return itemId to Replacement(recordId, Instant.ofEpochMilli(occurredAt), mm)
}

data class CareRow(val item: MaintenanceItemEntity, val care: CareItem, val life: CareLife)

/** 항목마다 교체 기록을 묶어 남은 주기를 낸다. 모르는 kind의 항목은 건너뛴다. 순서는 CareItem 순서. */
fun careRows(items: List<MaintenanceItemEntity>, records: List<RecordEntity>, currentOdometerMm: Long?): List<CareRow> {
    val byItem = records.mapNotNull { it.replacement() }.groupBy({ it.first }, { it.second })
    return items.mapNotNull { e -> e.careItem()?.let { c -> CareRow(e, c, careLife(c, e.baseline(), byItem[e.itemId].orEmpty(), currentOdometerMm)) } }
        .sortedBy { it.care.ordinal }
}

fun List<CareRow>.lifePcts(): List<Int> = mapNotNull { it.life.lifePct }

fun VehicleState.odometerMm(): Long? = odometerKm.valueOrNull()?.let { (it * 1_000_000).roundToLong() }
