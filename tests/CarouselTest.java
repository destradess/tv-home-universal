package io.github.tvhome.universal;
public class CarouselTest {
 public static void main(String[] args){
  int checks=0;
  for(int viewport:new int[]{640,864,1184,1824})for(int count=0;count<=80;count++){
   int pad=20,gap=20,width=CarouselPolicy.cardWidth(viewport,pad,gap,5),pitch=width+gap;
   if(CarouselPolicy.offset(0,pitch)!=0||CarouselPolicy.offset(1,pitch)!=0)throw new AssertionError("first two move");
   for(int i=0;i<count;i++){
    int offset=CarouselPolicy.offset(i,pitch);
    int visibleLeft=pad+i*pitch-offset;
    if(visibleLeft!=(i==0?pad:pad+pitch))throw new AssertionError("focus slot");
    int rightPad=count>2?Math.max(pad,viewport-pad-2*width-gap):pad;
    int content=2*pad+count*width+Math.max(0,count-1)*gap+(rightPad-pad);
    if(offset>Math.max(0,content-viewport))throw new AssertionError("last app unreachable");
    if(i>0&&AppMovePolicy.target(i,count,Math.max(1,count),-1,0)!=i-1)throw new AssertionError("move left");
    if(i<count-1&&AppMovePolicy.target(i,count,Math.max(1,count),1,0)!=i+1)throw new AssertionError("move right");
    checks++;
   }
  }
  System.out.println("PASS: "+checks+" carousel positions, first-two threshold, tail reachability and reorder navigation.");
 }
}
