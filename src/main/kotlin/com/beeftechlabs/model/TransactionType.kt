package com.beeftechlabs.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    Send, Receive, Delegate, Undelegate, Withdraw, Claim, Compound,
    Swap, Wrap, Unwrap, EnterFarm, ExitFarm, EnterLP, ExitLP,
    ModifyDelegationCap, ChangeServiceFee,
    ReceiveValidationReward,
    Unknown
}