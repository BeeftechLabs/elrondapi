package com.beeftechlabs.model.transaction

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    Send, Receive, Delegate, Undelegate, Withdraw, Claim, Compound,
    Swap, Wrap, Unwrap, EnterFarm, ExitFarm, EnterLP, ExitLP,
    ModifyDelegationCap, ChangeServiceFee,
    ReceiveValidationReward,
    SmartContract,
    Unknown
}