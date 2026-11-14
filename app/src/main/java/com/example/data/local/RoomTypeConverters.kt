package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.PaymentMethod
import com.example.domain.model.SmsProcessDecision
import com.example.domain.model.SpendingGuardLevel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.math.BigDecimal

class RoomTypeConverters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

    @TypeConverter
    fun fromSpendingGuardLevel(value: SpendingGuardLevel): String = value.name

    @TypeConverter
    fun toSpendingGuardLevel(value: String): SpendingGuardLevel = SpendingGuardLevel.valueOf(value)

    @TypeConverter
    fun fromSmsProcessDecision(value: SmsProcessDecision): String = value.name

    @TypeConverter
    fun toSmsProcessDecision(value: String): SmsProcessDecision = SmsProcessDecision.valueOf(value)
}
