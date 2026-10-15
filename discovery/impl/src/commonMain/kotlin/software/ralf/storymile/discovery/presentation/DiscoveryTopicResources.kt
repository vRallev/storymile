package software.ralf.storymile.discovery.presentation

import org.jetbrains.compose.resources.StringResource
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.topic_culture
import software.ralf.storymile.discovery.impl.generated.resources.topic_nature
import software.ralf.storymile.discovery.impl.generated.resources.topic_science
import software.ralf.storymile.discovery.impl.generated.resources.topic_technology

internal val DiscoveryTopic.titleResource: StringResource
  get() =
    when (this) {
      DiscoveryTopic.NATURE -> Res.string.topic_nature
      DiscoveryTopic.SCIENCE -> Res.string.topic_science
      DiscoveryTopic.CULTURE -> Res.string.topic_culture
      DiscoveryTopic.TECHNOLOGY -> Res.string.topic_technology
    }
