/** @type {import('tailwindcss').Config} */
const neutrals = {50:'#F1F4F8',100:'#E8EDF3',200:'#D4DDE7',300:'#B6C4D3',400:'#71849A',500:'#526278',600:'#526278',700:'#35506E',800:'#1D2B3F',900:'#13243B'};
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: { extend: {
    fontFamily: { sans: ['"Source Sans 3"','ui-sans-serif','system-ui','sans-serif'], display: ['"Source Sans 3"','ui-sans-serif','system-ui','sans-serif'] },
    colors: {
      white:'#FAFBFD', gray:neutrals, secondary:{DEFAULT:'#F1F4F8',...neutrals},
      primary:{DEFAULT:'#13243B',50:'#EAF0F6',100:'#DCE6F0',200:'#B8CCE0',300:'#8BA9C7',400:'#577DA3',500:'#355D85',600:'#2B4D70',700:'#244364',800:'#1D344F',900:'#13243B'},
      accent:{DEFAULT:'#355D85',50:'#EAF0F6',100:'#DCE6F0',200:'#B8CCE0',300:'#8BA9C7',400:'#577DA3',500:'#355D85',600:'#2B4D70',700:'#244364',800:'#1D344F',900:'#13243B'},
      success:{DEFAULT:'#246544',50:'#EDF6F0',100:'#CFE5D7',500:'#246544',600:'#246544',700:'#1C5738'},
      warning:{DEFAULT:'#8A570C',50:'#FBF4E7',100:'#EEDDAD',500:'#8A570C',600:'#8A570C',700:'#784A09'},
      error:{DEFAULT:'#A33434',50:'#FAEEEE',100:'#EBCACA',500:'#A33434',600:'#A33434',700:'#8E2929'}
    },
    boxShadow:{soft:'none',card:'none',hover:'none'}
  } },
  plugins: [],
};
