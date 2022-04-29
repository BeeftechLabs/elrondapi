package com.beeftechlabs.model.transaction

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    Transfer, Delegate, Undelegate, Withdraw, Claim, Compound,
    Swap, Wrap, Unwrap, EnterFarm, ExitFarm, EnterLP, ExitLP,
    ModifyDelegationCap, ChangeServiceFee,
    ReceiveValidationReward,
    SmartContract,
    Unknown
}