package io.github.tvhome.universal;
import java.util.*;
public final class LogicTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  check(AppOrder.merge(Arrays.asList("gone","b","b","a"),Arrays.asList("a"),Arrays.asList("a","b","c")).equals(Arrays.asList("b","a","c")));
  check(AppOrder.merge(Arrays.asList("gone"),Arrays.asList("b"),Arrays.asList("a","b")).equals(Arrays.asList("b","a")));
  check(AppOrder.merge(Collections.emptyList(),Collections.emptyList(),Collections.emptyList()).isEmpty());
  check(AppMovePolicy.target(4,12,5,1,0)==4); // no right-edge wrap
  check(AppMovePolicy.target(10,12,5,0,1)==10); // no missing row
  check(AppMovePolicy.target(9,12,5,0,1)==11); // short final row
  check(GreetingText.suffix("  alex  ").equals("ALEX"));
  HomeOverridePolicy policy=new HomeOverridePolicy();policy.configure("stock.home","stock.home.Home");
  check(!policy.shouldRedirect("stock.home","stock.home.Settings",true,10000));
  check(policy.shouldRedirect("stock.home","stock.home.Home",true,10000));
  check(!policy.shouldRedirect("stock.home","stock.home.Home",true,10100));
  policy.allowGoogleVisit();check(!policy.shouldRedirect("stock.home","stock.home.Home",true,12000));
  policy.homePressed(13000);check(policy.shouldRedirect("stock.home","stock.home.Home",true,15000));
  System.out.println("PASS: ordering, navigation boundaries, greetings, Home redirect and visit escape.");
 }
}
