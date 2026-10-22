package io.github.tvhome.universal;
final class AppMovePolicy {
    static int target(int index,int count,int columns,int dx,int dy){
        if(index<0||index>=count||columns<1)return index;
        if(dx<0)return index%columns>0?index-1:index;
        if(dx>0)return index%columns<columns-1&&index+1<count?index+1:index;
        if(dy<0)return index>=columns?index-columns:index;
        if(dy>0){int nextRow=(index/columns+1)*columns;return nextRow<count?Math.min(index+columns,count-1):index;}
        return index;
    }
}
