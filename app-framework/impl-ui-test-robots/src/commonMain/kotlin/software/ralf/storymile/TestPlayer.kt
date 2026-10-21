package software.ralf.storymile

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.ralf.storymile.player.Player
import software.ralf.storymile.player.PlayerImpl
import software.ralf.storymile.player.testing.FakePlayer
import software.ralf.storymile.user.UserScope

/** Supplies controlled audio state without opening an audio device. */
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<Player>(), replaces = [PlayerImpl::class])
class TestPlayer : FakePlayer()
