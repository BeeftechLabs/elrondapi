package com.beeftechlabs.repository.address.model

enum class AddressSort {
    AddressAsc, AddressDesc, BalanceAsc, BalanceDesc;

    companion object {

        fun valueOfOrDefault(value: String, default: AddressSort) = try {
            valueOf(value)
        } catch (ignored: Exception) {
            default
        }
    }
}