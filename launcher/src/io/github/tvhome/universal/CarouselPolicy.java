package io.github.tvhome.universal;
/** Keep the first two cards stationary, then anchor selection at slot two. */
final class CarouselPolicy {
    static int cardWidth(int viewport,int padding,int gap,int visible){
        return Math.max(1,(viewport-2*padding-gap*(Math.max(1,visible)-1))/Math.max(1,visible));
    }
    static int offset(int selected,int pitch){return Math.max(0,selected-1)*Math.max(0,pitch);}
}
