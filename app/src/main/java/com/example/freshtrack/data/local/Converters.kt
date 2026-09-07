package com.example.freshtrack.data.local

import androidx.room.TypeConverter
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.data.local.entities.OutboxOperationType
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.PriceSource
import java.time.LocalDate

/**
 * Storage forms for the types the schema uses.
 *
 * Two deliberate choices:
 *
 * A [LocalDate] is stored as its ISO string rather than an epoch number. It
 * keeps the column readable, it sorts correctly as text because ISO dates are
 * lexicographically ordered, and it cannot be accidentally reinterpreted in
 * another timezone the way a millisecond value can.
 *
 * Enums are stored by name, not ordinal. An ordinal silently changes meaning
 * the moment someone inserts a constant in the middle of the enum, which would
 * turn stored "use by" dates into "sell by" ones with no error anywhere.
 */
class Converters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun dateKindToString(value: DateKind?): String? = value?.name

    @TypeConverter
    fun stringToDateKind(value: String?): DateKind? = value?.let(DateKind::valueOf)

    @TypeConverter
    fun dateSourceToString(value: DateSource?): String? = value?.name

    @TypeConverter
    fun stringToDateSource(value: String?): DateSource? = value?.let(DateSource::valueOf)

    @TypeConverter
    fun priceSourceToString(value: PriceSource?): String? = value?.name

    @TypeConverter
    fun stringToPriceSource(value: String?): PriceSource? = value?.let(PriceSource::valueOf)

    @TypeConverter
    fun itemStateToString(value: ItemState?): String? = value?.name

    @TypeConverter
    fun stringToItemState(value: String?): ItemState? = value?.let(ItemState::valueOf)

    @TypeConverter
    fun locationTypeToString(value: LocationType?): String? = value?.name

    @TypeConverter
    fun stringToLocationType(value: String?): LocationType? = value?.let(LocationType::valueOf)

    @TypeConverter
    fun itemEventTypeToString(value: ItemEventType?): String? = value?.name

    @TypeConverter
    fun stringToItemEventType(value: String?): ItemEventType? = value?.let(ItemEventType::valueOf)

    @TypeConverter
    fun outboxOperationTypeToString(value: OutboxOperationType?): String? = value?.name

    @TypeConverter
    fun stringToOutboxOperationType(value: String?): OutboxOperationType? =
        value?.let(OutboxOperationType::valueOf)
}
