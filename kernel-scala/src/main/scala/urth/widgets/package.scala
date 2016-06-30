package urth

import declarativewidgets.{ChannelTrait, Channel, Default}
import org.apache.toree.kernel.api.Kernel

package object widgets {

  def initWidgets(implicit kernel: Kernel) = {
    kernel.err.println("The `urth` package name is deprecated. Will be removed in version 0.7.0. Use 'declarativewidgets' instead.")
    declarativewidgets.initWidgets
  }

  object WidgetChannels {
    def channel(chan: String = Default.Channel): ChannelTrait = {
      if( declarativewidgets.getKernel != null ) {
        declarativewidgets.getKernel.err.println("The `urth` package name is deprecated. Will be removed in version 0.7.0. Use 'declarativewidgets' instead.")
        declarativewidgets.getKernel.err.flush()
      }
      declarativewidgets.WidgetChannels.channel(chan)
    }
  }

}
