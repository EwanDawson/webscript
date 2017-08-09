package net.lazygun.webscript

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

/**
 * @author Ewan
 */
data class ExecutionContext @JsonCreator constructor(
    @JsonProperty val bindingsId: UUID,
    @JsonProperty val resolver: String
)