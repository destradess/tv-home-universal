package io.github.tvhome.universal;
final class SoundPacks {
    static final String[] IDS={"ps5","ps2","switch","xbox","persona","pipboy","portal"};
    static final String[] NAMES={"PS5","PS2 System","Switch","Xbox Series","Persona 5","Pip-Boy","Portal 2"};
    static final int[][] RESOURCES={
        {R.raw.ps5_navigation,R.raw.ps5_confirm,R.raw.ps5_back,R.raw.ps5_launch,R.raw.ps5_open,R.raw.ps5_close,R.raw.ps5_side_open,R.raw.ps5_side_close,R.raw.ps5_on,R.raw.ps5_off,R.raw.ps5_tab,R.raw.ps5_volume,R.raw.ps5_bump},
        {R.raw.ps2_navigation,R.raw.ps2_confirm,R.raw.ps2_back,R.raw.ps2_launch,R.raw.ps2_open,R.raw.ps2_close,R.raw.ps2_side_open,R.raw.ps2_side_close,R.raw.ps2_on,R.raw.ps2_off,R.raw.ps2_tab,R.raw.ps2_volume,R.raw.ps2_bump},
        {R.raw.switch_navigation,R.raw.switch_confirm,R.raw.switch_back,R.raw.switch_launch,R.raw.switch_open,R.raw.switch_close,R.raw.switch_side_open,R.raw.switch_side_close,R.raw.switch_on,R.raw.switch_off,R.raw.switch_tab,R.raw.switch_volume,R.raw.switch_bump},
        {R.raw.xbox_navigation,R.raw.xbox_confirm,R.raw.xbox_back,R.raw.xbox_launch,R.raw.xbox_open,R.raw.xbox_close,R.raw.xbox_side_open,R.raw.xbox_side_close,R.raw.xbox_on,R.raw.xbox_off,R.raw.xbox_tab,R.raw.xbox_volume,R.raw.xbox_bump},
        {R.raw.persona_navigation,R.raw.persona_confirm,R.raw.persona_back,R.raw.persona_launch,R.raw.persona_open,R.raw.persona_close,R.raw.persona_side_open,R.raw.persona_side_close,R.raw.persona_on,R.raw.persona_off,R.raw.persona_tab,R.raw.persona_volume,R.raw.persona_bump},
        {R.raw.pipboy_navigation,R.raw.pipboy_confirm,R.raw.pipboy_back,R.raw.pipboy_launch,R.raw.pipboy_open,R.raw.pipboy_close,R.raw.pipboy_side_open,R.raw.pipboy_side_close,R.raw.pipboy_on,R.raw.pipboy_off,R.raw.pipboy_tab,R.raw.pipboy_volume,R.raw.pipboy_bump},
        {R.raw.portal_navigation,R.raw.portal_confirm,R.raw.portal_back,R.raw.portal_launch,R.raw.portal_open,R.raw.portal_close,R.raw.portal_side_open,R.raw.portal_side_close,R.raw.portal_on,R.raw.portal_off,R.raw.portal_tab,R.raw.portal_volume,R.raw.portal_bump}
    };
    static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 1;}
}
